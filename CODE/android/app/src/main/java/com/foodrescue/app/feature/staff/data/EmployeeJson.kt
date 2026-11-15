package com.foodrescue.app.feature.staff.data

import com.foodrescue.app.feature.staff.domain.*
import org.json.JSONArray
import org.json.JSONObject

internal data class EmployeeRecord(val ownerEmail: String, val employee: StoreEmployee)

internal fun encodeEmployees(records: List<EmployeeRecord>): String = JSONObject().apply {
    put("version", 1)
    put("employees", JSONArray().apply { records.forEach { record -> put(JSONObject().apply {
        val member = record.employee
        put("owner", record.ownerEmail); put("id", member.id); put("store", member.storeId)
        put("name", member.name); put("email", member.email); put("phone", member.phone)
        put("permissions", JSONArray(member.permissions.map { it.name })); put("status", member.status.name)
        put("revision", member.revision); put("created", member.createdAtMillis); put("updated", member.updatedAtMillis)
        put("events", JSONArray().apply { member.events.forEach { event -> put(JSONObject().apply {
            put("type", event.type.name); put("at", event.atMillis); put("note", event.note)
            put("permissions", JSONArray(event.permissions.map { it.name }))
        }) } })
    }) } })
}.toString()

internal fun decodeEmployees(raw: String): List<EmployeeRecord> {
    val root = JSONObject(raw)
    require(root.getInt("version") == 1)
    val array = root.getJSONArray("employees")
    val records = (0 until array.length()).map { index ->
        val row = array.getJSONObject(index)
        val history = row.getJSONArray("events")
        val member = StoreEmployee(row.getString("id"), row.getInt("store"), row.getString("name"), row.getString("email"),
            row.getString("phone"), row.permissions(), EmployeeStatus.valueOf(row.getString("status")), row.getInt("revision"),
            row.getLong("created"), row.getLong("updated"), (0 until history.length()).map { position ->
                val event = history.getJSONObject(position)
                EmployeeEvent(EmployeeEventType.valueOf(event.getString("type")), event.getLong("at"), event.permissions(), event.getString("note"))
            })
        require(member.id.isNotBlank() && member.revision >= 1 && member.events.isNotEmpty())
        require(member.email == employeeEmail(member.email) && member.toDraft().validationIssues().isEmpty())
        val owner = row.getString("owner")
        require(owner.isNotBlank() && owner == employeeEmail(owner) && owner != member.email)
        EmployeeRecord(owner, member)
    }
    require(records.map { it.employee.id }.distinct().size == records.size)
    require(records.map { it.employee.email }.distinct().size == records.size)
    return records
}
private fun JSONObject.permissions(): Set<EmployeePermission> {
    val items = getJSONArray("permissions")
    return (0 until items.length()).map { EmployeePermission.valueOf(items.getString(it)) }.toSet()
}
