// AC-02,05,06,07,09,10,13: StoreHistoryRecorder responsibility.
package com.foodrescue.backend.store.service

import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.audit.AuditService
import com.foodrescue.backend.store.entity.Store
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/** Store change history = audit_log rows with before/after JSON (T3.3 Q5, BR-56). Call inside the changing transaction. */
@Component
class StoreHistoryRecorder(private val audit: AuditService, private val mapper: ObjectMapper) {
    fun record(actor: Account, store: Store, entityType: String, entityId: UUID, action: String,
               before: Map<String, Any?>?, after: Map<String, Any?>?) {
        audit.record(actor, entityType, entityId, action, store, after?.let(mapper::writeValueAsString),
            before?.let(mapper::writeValueAsString))
    }

    companion object {
        const val STORE = "STORE"
        const val CHANGE_REQUEST = "STORE_CHANGE_REQUEST"
        /** T3.4 Q8: staff changes are part of the store history. */
        val ENTITY_TYPES = listOf(STORE, CHANGE_REQUEST, "STAFF_ASSIGNMENT", "MENU_ITEM", "PRICE_DOCUMENT")
    }
}
