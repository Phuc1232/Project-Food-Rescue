package com.foodrescue.app.feature.account.data

import com.foodrescue.app.feature.account.domain.*
import org.json.JSONArray
import org.json.JSONObject

internal object AccountJson {
    fun encode(records: List<AccountRecord>): String = JSONObject().apply {
        put("version", 1)
        put("accounts", JSONArray().apply { records.forEach { record -> put(JSONObject().apply {
            put("key", record.key); put("revision", record.revision)
            record.profile?.let { put("profile", JSONObject().apply {
                put("name", it.name); put("email", it.email); put("phone", it.phone)
            }) }
            record.firstSeenAt?.let { put("firstSeen", it) }; record.lastLoginAt?.let { put("lastLogin", it) }
            put("events", JSONArray().apply { record.events.forEach { event -> put(JSONObject().apply {
                put("id", event.id); put("status", event.status.name); put("reason", event.reason)
                put("actor", event.actor); put("at", event.atMillis)
            }) } })
        }) } })
    }.toString()

    fun decode(raw: String): List<AccountRecord> {
        val root = JSONObject(raw); require(root.getInt("version") == 1)
        val rows = root.getJSONArray("accounts")
        val records = (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            val profile = row.optJSONObject("profile")?.let {
                CustomerProfile(it.getString("name"), it.getString("email"), it.getString("phone")).also { profile ->
                    require(profile.email == accountEmail(profile.email) && profile.email.isNotBlank())
                    require(LocalAccountStore.key(profile.email) == row.getString("key"))
                    require(profile.name.isNotBlank() && profile.name.length <= 120 && isValidCustomerPhone(profile.phone))
                }
            }
            val events = row.getJSONArray("events")
            AccountRecord(row.getString("key"), profile,
                if (row.has("firstSeen")) row.getLong("firstSeen") else null,
                if (row.has("lastLogin")) row.getLong("lastLogin") else null, row.getLong("revision"),
                (0 until events.length()).map { position -> events.getJSONObject(position).let {
                    AccountStatusEvent(it.getString("id"), AccountStatus.valueOf(it.getString("status")),
                        it.getString("reason"), it.getString("actor"), it.getLong("at"))
                } })
        }
        require(records.map { it.key }.distinct().size == records.size)
        return records
    }
}

