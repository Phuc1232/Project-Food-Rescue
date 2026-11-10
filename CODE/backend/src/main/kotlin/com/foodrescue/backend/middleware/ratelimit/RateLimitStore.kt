// AC-15,25,26: RateLimitStore responsibility.
package com.foodrescue.backend.middleware.ratelimit

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.*
@Repository
class RateLimitStore(private val jdbc: JdbcTemplate) {
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    fun count(policy: String,key: String,increment: Boolean): Pair<Int,Long> {
        // DB clock and atomic UPSERT make quotas consistent across instances.
        val amount=if(increment) 1 else 0
        return jdbc.queryForObject("""
            WITH w AS (SELECT to_timestamp(floor(extract(epoch FROM clock_timestamp()) / 900) * 900) AS start)
            INSERT INTO auth_rate_limit(policy,key_hash,window_start,count,expires_at)
            SELECT ?,?,start,?,start+interval '15 minutes' FROM w
            ON CONFLICT(policy,key_hash,window_start) DO UPDATE SET count=auth_rate_limit.count+excluded.count
            RETURNING count, greatest(1,ceil(extract(epoch FROM expires_at-clock_timestamp())))::bigint AS retry
        """, { rs,_ -> rs.getInt("count") to rs.getLong("retry") },policy,key,amount)
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    fun cleanup() { jdbc.update("DELETE FROM auth_rate_limit WHERE ctid IN (SELECT ctid FROM auth_rate_limit WHERE expires_at < now() LIMIT 1000)") }
}
