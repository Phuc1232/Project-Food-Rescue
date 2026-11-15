// AC-06 (T5.1): StoreSearchService responsibility.
package com.foodrescue.backend.search.service

import com.foodrescue.backend.common.response.FieldViolation
import com.foodrescue.backend.common.response.PageResponse
import com.foodrescue.backend.search.dto.StoreCard
import com.foodrescue.backend.search.dto.StorePin
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.util.UUID

/** UC-09 store search and UC-11 map; only ACTIVE stores (StoreSalesPolicy rule, T3.3). */
@Service
class StoreSearchService(private val jdbc: NamedParameterJdbcTemplate, private val clock: Clock) {
    @Transactional(readOnly = true)
    fun search(q: String?, lat: Double?, lng: Double?, radiusKm: Double?, page: Int, size: Int): PageResponse<StoreCard> {
        val errors = mutableListOf<FieldViolation>()
        val geo = SearchParams.geo(lat, lng, radiusKm, errors); SearchParams.page(page, size, errors)
        if (q != null && q.trim().length > 100) errors += FieldViolation("q", "At most 100 characters")
        SearchParams.throwIfAny(errors)
        val p = MapSqlParameterSource("now", Timestamp.from(clock.instant()))
        val where = mutableListOf("s.status = 'ACTIVE'")
        q?.trim()?.takeIf { it.isNotEmpty() }?.let { where += "lower(s.name) LIKE :q"; p.addValue("q", "%" + it.lowercase().replace("%", "").replace("_", "") + "%") }
        geo?.let {
            where += Geo.BOX_SQL; where += "${Geo.DISTANCE_SQL} <= :radius"
            p.addValue("lat", it.lat).addValue("lng", it.lng).addValue("radius", it.radiusKm).addValue("minLat", it.lat - it.latDelta)
                .addValue("maxLat", it.lat + it.latDelta).addValue("minLng", it.lng - it.lngDelta).addValue("maxLng", it.lng + it.lngDelta)
        }
        val clause = where.joinToString(" AND ")
        val total = jdbc.queryForObject("SELECT count(*) FROM store s WHERE $clause", p, Long::class.java)!!
        p.addValue("limit", size).addValue("offset", page * size)
        val distance = if (geo != null) Geo.DISTANCE_SQL else "NULL::float8"
        val rows = jdbc.query("""SELECT s.id, s.name, s.address, s.latitude, s.longitude, $distance AS distance_km, $OPEN_OFFERS AS offers
            FROM store s WHERE $clause ORDER BY ${if (geo != null) "distance_km" else "s.name"}, s.id LIMIT :limit OFFSET :offset""", p) { rs, _ ->
            StoreCard(rs.getObject("id", UUID::class.java), rs.getString("name"), rs.getString("address"), rs.getBigDecimal("latitude"),
                rs.getBigDecimal("longitude"), rs.getObject("distance_km")?.let { Geo.round((it as Number).toDouble()) }, rs.getLong("offers"),
                Geo.directions(rs.getBigDecimal("latitude"), rs.getBigDecimal("longitude")))
        }
        return PageResponse(rows, page, size, total, ((total + size - 1) / size).toInt())
    }

    @Transactional(readOnly = true)
    fun map(minLat: Double, maxLat: Double, minLng: Double, maxLng: Double): List<StorePin> {
        val errors = mutableListOf<FieldViolation>()
        if (minLat !in -90.0..90.0 || maxLat !in -90.0..90.0 || minLat >= maxLat) errors += FieldViolation("maxLat", "Invalid latitude range")
        if (minLng !in -180.0..180.0 || maxLng !in -180.0..180.0 || minLng >= maxLng) errors += FieldViolation("maxLng", "Invalid longitude range")
        if (maxLat - minLat > 2.0 || maxLng - minLng > 2.0) errors += FieldViolation("bounds", "Area must be at most 2 x 2 degrees")
        SearchParams.throwIfAny(errors)
        val p = MapSqlParameterSource("now", Timestamp.from(clock.instant())).addValue("minLat", minLat).addValue("maxLat", maxLat)
            .addValue("minLng", minLng).addValue("maxLng", maxLng)
        return jdbc.query("""SELECT s.id, s.name, s.latitude, s.longitude, $OPEN_OFFERS > 0 AS has_offers FROM store s
            WHERE s.status = 'ACTIVE' AND ${Geo.BOX_SQL} ORDER BY s.name, s.id LIMIT 500""", p) { rs, _ ->
            StorePin(rs.getObject("id", UUID::class.java), rs.getString("name"), rs.getBigDecimal("latitude"), rs.getBigDecimal("longitude"), rs.getBoolean("has_offers"))
        }
    }

    private companion object {
        val OPEN_OFFERS = "(SELECT count(*) FROM sale_batch b WHERE b.store_id = s.id AND b.status = 'OPEN' AND b.order_deadline > :now AND " +
            "${com.foodrescue.backend.surprisebox.service.BatchAvailability.AVAILABLE_COUNT_SQL} > 0)"
    }
}
