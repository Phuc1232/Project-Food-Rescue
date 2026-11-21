package com.foodrescue.app.feature.store.data

import com.foodrescue.app.core.common.GeoPoint
import com.foodrescue.app.feature.store.domain.*
import org.json.JSONArray
import org.json.JSONObject

/** Additive v1 fields preserve existing profiles, payout accounts and public-map readers. */
internal object OwnerStoreJson {
    fun encode(store: OwnerStore): String = JSONObject().apply {
        put("version", 1); put("status", store.status.name); put("revision", store.revision)
        put("submittedAt", store.submittedAtMillis); put("reviewNote", store.reviewNote)
        put("profile", profileJson(store.profile))
        put("restrictions", JSONArray().apply { store.governance.events.forEach { event -> put(JSONObject().apply {
            put("id", event.id); put("action", event.action.name); put("batchId", event.batchId)
            put("batchName", event.batchName); put("note", event.note); put("actor", event.actor); put("at", event.atMillis)
        }) } })
        store.payout?.let { account -> put("payout", JSONObject().apply {
            put("bank", account.bank); put("holder", account.holder); put("number", account.number)
            put("bankCode", LocalPayoutBanks.all.findBank(account.bank)?.code)
        }) }
        put("reviewHistory", JSONArray().apply { store.reviewHistory.forEach { event -> put(JSONObject().apply {
            put("action", event.action.name); put("at", event.atMillis); put("actor", event.actor); put("note", event.note)
            event.submittedProfile?.let { put("profile", profileJson(it)) }
        }) } })
    }.toString()

    fun decode(raw: String): OwnerStore {
        val json = JSONObject(raw)
        require(json.getInt("version") == 1)
        val events = json.optJSONArray("reviewHistory") ?: JSONArray()
        val restrictions = if (json.has("restrictions")) json.getJSONArray("restrictions") else JSONArray()
        return OwnerStore(
            profile = profile(json.getJSONObject("profile")),
            status = StoreReviewStatus.valueOf(json.getString("status")),
            submittedAtMillis = if (json.isNull("submittedAt")) null else json.getLong("submittedAt"),
            reviewNote = json.optString("reviewNote"),
            payout = json.optJSONObject("payout")?.let { StorePayoutAccount(
                LocalPayoutBanks.all.findBank(it.optString("bankCode"))?.name ?: it.getString("bank"),
                it.getString("holder"), it.getString("number")) },
            revision = json.optLong("revision", 0),
            governance = StoreGovernance(List(restrictions.length()) { index -> restrictions.getJSONObject(index).let {
                StoreRestrictionEvent(it.getString("id"), StoreRestrictionAction.valueOf(it.getString("action")),
                    if (it.isNull("batchId")) null else it.getString("batchId"),
                    if (it.isNull("batchName")) null else it.getString("batchName"),
                    it.getString("note"), it.getString("actor"), it.getLong("at"))
            } }),
            reviewHistory = List(events.length()) { index -> events.getJSONObject(index).let {
                StoreReviewEvent(StoreReviewAction.valueOf(it.getString("action")), it.getLong("at"),
                    it.getString("actor"), it.optString("note"), it.optJSONObject("profile")?.let(::profile))
            } },
        )
    }

    private fun profileJson(p: OwnerStoreProfile) = JSONObject().apply {
        put("name", p.name); put("category", p.category); put("contact", p.contactName)
        put("phone", p.phone); put("address", p.address); put("area", p.area); put("description", p.description)
        put("open", p.opensAt); put("close", p.closesAt); put("contactEmail", p.contactEmail)
        p.location?.let { put("location", JSONObject().apply { put("latitude", it.latitude); put("longitude", it.longitude) }) }
    }

    private fun profile(p: JSONObject) = OwnerStoreProfile(p.getString("name"), p.getString("category"), p.getString("contact"),
        p.getString("phone"), p.getString("address"), p.getString("area"), p.getString("description"),
        p.getString("open"), p.getString("close"), p.optJSONObject("location")?.let {
            GeoPoint(it.getDouble("latitude"), it.getDouble("longitude"))
        }, p.optString("contactEmail"))
}
