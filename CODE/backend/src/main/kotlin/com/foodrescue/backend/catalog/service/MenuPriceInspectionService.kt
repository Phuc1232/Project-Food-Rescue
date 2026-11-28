// AC-01..07 (T8.5): MenuPriceInspectionService responsibility.
package com.foodrescue.backend.catalog.service
import com.foodrescue.backend.catalog.dto.*
import com.foodrescue.backend.catalog.MenuItemStatus
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.response.PageResponse
import com.foodrescue.backend.common.validation.PageQuery
import com.foodrescue.backend.store.repository.StoreRepository
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.security.access.prepost.PreAuthorize
import java.util.UUID
@Service
@PreAuthorize("hasRole('ADMIN')")
@Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
class MenuPriceInspectionService(private val stores: StoreRepository,private val jdbc: NamedParameterJdbcTemplate) {
    fun inspect(storeId: UUID,itemId: UUID?,status: MenuItemStatus?,onlyMismatch: Boolean,page: Int,size: Int): StoreMenuPriceAuditView {
        val store=stores.findById(storeId).orElse(null) ?: throw ApiException(404,"NOT_FOUND","Store not found")
        PageQuery().pageable(page,size,"createdAt")
        val args=mapOf("store" to storeId,"item" to itemId,"status" to status?.name)
        if(itemId!=null && jdbc.queryForObject("SELECT count(*) FROM menu_item WHERE id=:item AND store_id=:store",args,Int::class.java)==0) throw ApiException(404,"NOT_FOUND","Menu item not found in store")
        val items=jdbc.query("""SELECT m.*,c.code category_code,c.name category_name FROM menu_item m JOIN food_category c ON c.id=m.category_id
            WHERE m.store_id=:store AND (cast(:item AS uuid) IS NULL OR m.id=:item) AND (cast(:status AS text) IS NULL OR m.status=:status) ORDER BY m.name,m.id""",args) { rs,_ ->
            MenuItemView(rs.getObject("id",UUID::class.java),rs.getString("name"),rs.getString("category_code"),rs.getString("category_name"),rs.getBigDecimal("listed_price").setScale(0),rs.getString("ingredients"),rs.getString("safety_warnings"),rs.getString("status"),rs.getLong("version"))
        }
        if(items.isEmpty()) return StoreMenuPriceAuditView(storeId,store.name,store.status.name,PageResponse(emptyList(),page,size,0,0))
        val batch=mapOf("store" to storeId,"ids" to items.map { it.id })
        val histories=jdbc.query("""SELECT h.*,a.display_name FROM menu_price_history h JOIN account a ON a.id=h.changed_by_id
            WHERE h.menu_item_id IN (:ids) ORDER BY h.changed_at DESC,h.created_at DESC,h.id""",batch) { rs,_ -> rs.getObject("menu_item_id",UUID::class.java) to MenuPriceAuditHistoryItem(rs.getBigDecimal("previous_price").setScale(0),rs.getBigDecimal("new_price").setScale(0),rs.getTimestamp("changed_at").toInstant(),rs.getObject("changed_by_id",UUID::class.java),rs.getString("display_name"),rs.getString("reason")) }.groupBy({it.first},{it.second})
        val docs=jdbc.query("""SELECT d.*,f.original_name FROM price_reference_document d JOIN stored_file f ON f.id=d.file_id
            WHERE d.store_id=:store AND (d.menu_item_id IS NULL OR d.menu_item_id IN (:ids)) ORDER BY d.uploaded_at DESC,d.id""",batch) { rs,_ ->
            PriceDocumentView(rs.getObject("id",UUID::class.java),rs.getObject("menu_item_id",UUID::class.java),rs.getObject("file_id",UUID::class.java),rs.getString("original_name"),rs.getString("description"),rs.getDate("applies_from").toLocalDate(),rs.getTimestamp("uploaded_at").toInstant(),rs.getObject("replaced_by_id",UUID::class.java))
        }
        val prices=items.associate { it.id to it.listedPrice }
        val packed=jdbc.query("""SELECT bi.menu_item_id,bi.unit_price_snapshot,count(DISTINCT bi.box_id) box_count,sum(bi.quantity) total_quantity,min(pb.packed_at) first_packed_at,max(pb.packed_at) last_packed_at
            FROM box_item bi JOIN physical_box pb ON pb.id=bi.box_id WHERE bi.store_id=:store AND bi.menu_item_id IN (:ids)
            GROUP BY bi.menu_item_id,bi.unit_price_snapshot ORDER BY max(pb.packed_at) DESC,bi.unit_price_snapshot""",batch) { rs,_ ->
            val id=rs.getObject("menu_item_id",UUID::class.java);val price=rs.getBigDecimal("unit_price_snapshot").setScale(0);val current=prices.getValue(id)
            id to PackedBoxPriceSummaryView(price,rs.getLong("box_count"),rs.getLong("total_quantity"),rs.getTimestamp("first_packed_at").toInstant(),rs.getTimestamp("last_packed_at").toInstant(),current.compareTo(price)!=0,current-price)
        }.groupBy({it.first},{it.second})
        val views=items.map { i -> val summaries=packed[i.id].orEmpty();MenuItemPriceAuditView(i,histories[i.id].orEmpty(),docs.filter { it.menuItemId==null || it.menuItemId==i.id },summaries,summaries.any { it.differsFromCurrentListedPrice }) }.filter { !onlyMismatch || it.hasPackedPriceMismatch }
        val start=(page.toLong()*size).coerceAtMost(views.size.toLong()).toInt()
        return StoreMenuPriceAuditView(storeId,store.name,store.status.name,PageResponse(views.subList(start,(start+size).coerceAtMost(views.size)),page,size,views.size.toLong(),(views.size+size-1)/size))
    }
}
