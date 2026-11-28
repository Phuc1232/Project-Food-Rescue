// AC-01..07 (T8.5): AdminActivityLogService responsibility.
package com.foodrescue.backend.audit.service
import com.foodrescue.backend.audit.dto.AdminAuditLogItem
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.response.PageResponse
import com.foodrescue.backend.common.validation.PageQuery
import com.foodrescue.backend.store.repository.StoreRepository
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.security.access.prepost.PreAuthorize
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.JsonNode
import tools.jackson.databind.DeserializationFeature
import java.util.UUID
import java.time.Instant
import java.sql.Timestamp
import java.util.Locale
@Service
@PreAuthorize("hasRole('ADMIN')")
@Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
class AdminActivityLogService(private val stores: StoreRepository,private val jdbc: NamedParameterJdbcTemplate,private val mapper: ObjectMapper) {
    private val preciseJson=mapper.readerFor(Any::class.java).with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
    companion object { val MONITORED_ENTITY_TYPES=setOf("STORE","STORE_CHANGE_REQUEST","PARTNER_APPLICATION","STAFF_ASSIGNMENT","MENU_ITEM","PRICE_DOCUMENT","BOX_TEMPLATE","SALE_BATCH","PHYSICAL_BOX","CUSTOMER_ORDER","PAYMENT","REFUND_REQUEST","COMPLAINT","STORE_PAYOUT_ACCOUNT","SETTLEMENT","STORE_PAYOUT","FINANCIAL_POLICY","ACCOUNT","AUTH") }
    private fun safe(raw: String?): JsonNode? {
        if(raw==null) return null
        fun scrub(value: Any?): Any? = when(value) {
            is Map<*,*> -> value.entries.associate { (key,v) ->
                val name=key.toString();val normalized=name.lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)
                name to when { normalized=="accountnumber" -> v?.toString()?.let { "*".repeat((it.length-4).coerceAtLeast(2))+it.takeLast(4) }
                    normalized in setOf("password","passwordhash","accesstoken","refreshtoken","token","secret","privatekey","apikey","checksumkey","accountnumberciphertext") -> "[REDACTED]"
                    else -> scrub(v) }
            }
            is List<*> -> value.map { scrub(it) }
            else -> value
        }
        return try {preciseJson.readTree(mapper.writeValueAsString(scrub(preciseJson.readValue<Any>(raw))))} catch (_: Exception) {null}
    }
    fun list(storeId: UUID?,entityType: String?,entityId: UUID?,actorId: UUID?,action: String?,from: Instant?,to: Instant?,page: Int,size: Int,sort: String): PageResponse<AdminAuditLogItem> {
        if(storeId!=null && !stores.existsById(storeId)) throw ApiException(404,"NOT_FOUND","Store not found")
        if(from!=null && to!=null && from>to) throw ApiException(400,"VALIDATION_FAILED","from must not be after to")
        val type=entityType?.trim()?.uppercase(Locale.ROOT)
        if(type!=null && type !in MONITORED_ENTITY_TYPES) throw ApiException(400,"VALIDATION_FAILED","Unknown entityType")
        PageQuery().pageable(page,size,"createdAt")
        val column=mapOf("occurredAt" to "a.occurred_at","createdAt" to "a.created_at")[sort] ?: throw ApiException(400,"INVALID_PAGINATION","Invalid sort")
        val where=""" WHERE (cast(:store AS uuid) IS NULL OR a.store_id=:store) AND (cast(:type AS text) IS NULL OR a.entity_type=:type)
            AND (cast(:entity AS uuid) IS NULL OR a.entity_id=:entity) AND (cast(:actor AS uuid) IS NULL OR a.actor_id=:actor)
            AND (cast(:action AS text) IS NULL OR upper(a.action)=:action) AND (cast(:from AS timestamptz) IS NULL OR a.occurred_at>=:from) AND (cast(:to AS timestamptz) IS NULL OR a.occurred_at<=:to)"""
        val args=mapOf("store" to storeId,"type" to type,"entity" to entityId,"actor" to actorId,"action" to action?.trim()?.uppercase(Locale.ROOT),"from" to from?.let(Timestamp::from),"to" to to?.let(Timestamp::from),"limit" to size,"offset" to page.toLong()*size)
        val count=jdbc.queryForObject("SELECT count(*) FROM audit_log a$where",args,Long::class.java)!!
        val records=jdbc.queryForList("""SELECT a.*,s.name store_name,s.owner_id,actor.login_identifier,actor.display_name FROM audit_log a
            LEFT JOIN store s ON s.id=a.store_id LEFT JOIN account actor ON actor.id=a.actor_id $where ORDER BY $column DESC,a.id LIMIT :limit OFFSET :offset""",args)
        val actors=records.mapNotNull {it["actor_id"] as? UUID}.distinct()
        val admins=if(actors.isEmpty()) emptySet() else jdbc.queryForList("SELECT account_id FROM account_role WHERE role='ADMIN' AND account_id IN (:ids)",mapOf("ids" to actors),UUID::class.java).filterNotNull().toSet()
        val staff=if(actors.isEmpty()) emptySet() else jdbc.query("SELECT DISTINCT store_id,account_id FROM staff_assignment WHERE account_id IN (:ids)",mapOf("ids" to actors)) {rs,_ -> rs.getObject("store_id",UUID::class.java) to rs.getObject("account_id",UUID::class.java)}.toSet()
        val views=records.map { r ->
            val actor=r["actor_id"] as? UUID;val store=r["store_id"] as? UUID
            val role=when {actor==null -> "SYSTEM";actor==r["owner_id"] -> "OWNER";actor in admins -> "ADMIN";(store to actor) in staff -> "STAFF";else -> "CUSTOMER"}
            AdminAuditLogItem(r["id"] as UUID,store,r["store_name"] as? String,r["entity_type"] as String,r["entity_id"] as UUID,r["action"] as String,actor,r["login_identifier"] as? String,r["display_name"] as? String,role,r["correlation_id"] as? String,(r["occurred_at"] as Timestamp).toInstant(),safe(r["before_state"] as? String),safe(r["after_state"] as? String))
        }
        return PageResponse(views,page,size,count,((count+size-1)/size).toInt())
    }
}
