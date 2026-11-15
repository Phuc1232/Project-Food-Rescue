// AC-01..05,07 (T5.1): OfferQueryService responsibility.
package com.foodrescue.backend.search.service

import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.response.FieldViolation
import com.foodrescue.backend.common.response.PageResponse
import com.foodrescue.backend.file.storage.FileStorage
import com.foodrescue.backend.search.dto.OfferDetail
import com.foodrescue.backend.search.dto.OfferSummary
import com.foodrescue.backend.surprisebox.service.BatchAvailability
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.InputStream
import java.math.BigDecimal
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.util.UUID

class CoverImage(val mimeType: String, val size: Long, val stream: InputStream)

/** UC-07, UC-08, UC-10 for signed-in customers (D10). Visibility follows BR-T51-01/02; items are never exposed (BR-05). */
@Service
class OfferQueryService(private val jdbc: NamedParameterJdbcTemplate, private val storage: FileStorage, private val clock: Clock) {
    @Transactional(readOnly = true)
    fun search(lat: Double?, lng: Double?, radiusKm: Double?, categoryCode: String?, minPrice: BigDecimal?, maxPrice: BigDecimal?,
               pickupFrom: Instant?, pickupTo: Instant?, sort: String, page: Int, size: Int): PageResponse<OfferSummary> {
        val errors = mutableListOf<FieldViolation>()
        val geo = SearchParams.geo(lat, lng, radiusKm, errors)
        SearchParams.prices(minPrice, maxPrice, errors); SearchParams.window(pickupFrom, pickupTo, errors); SearchParams.page(page, size, errors)
        if (sort !in SORTS) errors += FieldViolation("sort", "One of $SORTS")
        if (sort == "distance" && geo == null) errors += FieldViolation("sort", "distance requires lat and lng")
        SearchParams.throwIfAny(errors)

        val p = MapSqlParameterSource("now", Timestamp.from(clock.instant()))
        val where = mutableListOf(ON_SALE)
        geo?.let {
            where += Geo.BOX_SQL; where += "${Geo.DISTANCE_SQL} <= :radius"
            p.addValue("lat", it.lat).addValue("lng", it.lng).addValue("radius", it.radiusKm)
                .addValue("minLat", it.lat - it.latDelta).addValue("maxLat", it.lat + it.latDelta)
                .addValue("minLng", it.lng - it.lngDelta).addValue("maxLng", it.lng + it.lngDelta)
        }
        categoryCode?.let { where += "c.code = :category"; p.addValue("category", it) }
        minPrice?.let { where += "b.selling_price >= :minPrice"; p.addValue("minPrice", it) }
        maxPrice?.let { where += "b.selling_price <= :maxPrice"; p.addValue("maxPrice", it) }
        pickupFrom?.let { where += "b.pickup_ends_at > :pickupFrom"; p.addValue("pickupFrom", Timestamp.from(it)) }
        pickupTo?.let { where += "b.pickup_starts_at < :pickupTo"; p.addValue("pickupTo", Timestamp.from(it)) }
        val clause = where.joinToString(" AND ")
        val total = jdbc.queryForObject("SELECT count(*) $FROM WHERE $clause", p, Long::class.java)!!
        val order = when (sort) { "distance" -> "distance_km"; "price" -> "b.selling_price"; else -> "b.pickup_starts_at" }
        val distance = if (geo != null) Geo.DISTANCE_SQL else "NULL::float8"
        p.addValue("limit", size).addValue("offset", page * size)
        val rows = jdbc.query("""SELECT b.id, s.id AS store_id, s.name AS store_name, t.name AS box_name, c.code, c.name AS category_name,
                b.selling_price, b.minimum_value, ${BatchAvailability.AVAILABLE_COUNT_SQL} AS available, b.order_deadline, b.pickup_starts_at,
                b.pickup_ends_at, $distance AS distance_km, t.cover_file_id IS NOT NULL AS has_cover
            $FROM WHERE $clause ORDER BY $order, b.id LIMIT :limit OFFSET :offset""", p) { rs, _ -> summary(rs) }
        return PageResponse(rows, page, size, total, ((total + size - 1) / size).toInt())
    }

    @Transactional(readOnly = true)
    fun detail(batchId: UUID): OfferDetail {
        val p = MapSqlParameterSource("now", Timestamp.from(clock.instant())).addValue("id", batchId)
        val d = jdbc.query("""SELECT b.id, b.status, b.order_deadline > :now AS before_deadline, t.name AS box_name, t.description, c.code,
                c.name AS category_name, b.selling_price, b.minimum_value, ${BatchAvailability.AVAILABLE_COUNT_SQL} AS available, b.order_deadline,
                b.pickup_starts_at, b.pickup_ends_at, b.safety_warnings, s.id AS store_id, s.name AS store_name, s.address, s.latitude, s.longitude,
                s.timezone, t.cover_file_id IS NOT NULL AS has_cover
            $FROM WHERE b.id = :id AND s.status = 'ACTIVE' AND b.status IN ('OPEN','PAUSED','ENDED')""", p) { rs, _ ->
            val available = rs.getLong("available"); val status = rs.getString("status")
            val reason = when {
                status == "PAUSED" -> "PAUSED"; status == "ENDED" -> "ENDED"
                !rs.getBoolean("before_deadline") -> "DEADLINE_PASSED"; available == 0L -> "SOLD_OUT"; else -> null
            }
            OfferDetail(rs.getObject("id", UUID::class.java), status, reason == null, reason, rs.getString("box_name"), rs.getString("description"),
                rs.getString("code"), rs.getString("category_name"), rs.getBigDecimal("selling_price").setScale(0), rs.getBigDecimal("minimum_value").setScale(0),
                available, rs.getTimestamp("order_deadline").toInstant(), rs.getTimestamp("pickup_starts_at").toInstant(),
                rs.getTimestamp("pickup_ends_at").toInstant(), listOf(rs.getString("safety_warnings")), rs.getObject("store_id", UUID::class.java),
                rs.getString("store_name"), rs.getString("address"), rs.getBigDecimal("latitude"), rs.getBigDecimal("longitude"), rs.getString("timezone"),
                Geo.directions(rs.getBigDecimal("latitude"), rs.getBigDecimal("longitude")), rs.getBoolean("has_cover"))
        }.firstOrNull() ?: throw ApiException(404, "NOT_FOUND", "Resource not found")
        // BR-19: warnings of the items in boxes that could be allocated, without naming the items.
        val boxWarnings = jdbc.queryForList("""SELECT DISTINCT i.safety_warnings_snapshot FROM box_item i JOIN physical_box p ON p.id = i.box_id
            JOIN sale_batch b ON b.id = p.current_batch_id WHERE b.id = :id AND p.status IN ('AVAILABLE','RESERVED','ALLOCATED') AND ${BatchAvailability.MEETS_TERMS}
            ORDER BY 1""", p, String::class.java).filterNotNull().map { it.trim() }.filter { it.isNotEmpty() }
        return d.copy(safetyWarnings = (d.safetyWarnings + boxWarnings).distinct())
    }

    /** Cover of a visible offer's template (closes the T4.2 gap: unattached files are otherwise uploader-only). */
    @Transactional(readOnly = true)
    fun cover(batchId: UUID): CoverImage {
        val f = jdbc.query("""SELECT f.storage_key, f.mime_type, f.size_bytes FROM sale_batch b JOIN store s ON s.id = b.store_id
            JOIN box_template t ON t.id = b.template_id JOIN stored_file f ON f.id = t.cover_file_id
            WHERE b.id = :id AND s.status = 'ACTIVE' AND b.status IN ('OPEN','PAUSED','ENDED')""", MapSqlParameterSource("id", batchId)) { rs, _ ->
            Triple(rs.getString(1), rs.getString(2), rs.getLong(3)) }.firstOrNull() ?: throw ApiException(404, "NOT_FOUND", "Resource not found")
        val stream = try { storage.open(f.first) } catch (_: java.io.IOException) { throw ApiException(503, "SERVICE_UNAVAILABLE", "File storage unavailable") }
        return CoverImage(f.second, f.third, stream)
    }

    private fun summary(rs: ResultSet) = OfferSummary(rs.getObject("id", UUID::class.java), rs.getObject("store_id", UUID::class.java),
        rs.getString("store_name"), rs.getString("box_name"), rs.getString("code"), rs.getString("category_name"),
        rs.getBigDecimal("selling_price").setScale(0), rs.getBigDecimal("minimum_value").setScale(0), rs.getLong("available"),
        rs.getTimestamp("order_deadline").toInstant(), rs.getTimestamp("pickup_starts_at").toInstant(), rs.getTimestamp("pickup_ends_at").toInstant(),
        rs.getObject("distance_km")?.let { Geo.round((it as Number).toDouble()) }, rs.getBoolean("has_cover"))

    companion object {
        const val FROM = "FROM sale_batch b JOIN store s ON s.id = b.store_id JOIN box_template t ON t.id = b.template_id JOIN food_category c ON c.id = t.category_id"
        /** BR-T51-01: OPEN, before the deadline, ACTIVE store, at least one sellable box. */
        const val ON_SALE = "b.status = 'OPEN' AND b.order_deadline > :now AND s.status = 'ACTIVE' AND ${BatchAvailability.AVAILABLE_COUNT_SQL} > 0"
        val SORTS = setOf("distance", "price", "pickupStartsAt")
    }
}
