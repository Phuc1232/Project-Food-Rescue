// AC-12: StoreSalesPolicy responsibility.
package com.foodrescue.backend.store.service

import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.store.StoreStatus
import com.foodrescue.backend.store.entity.Store
import org.springframework.stereotype.Component

/**
 * The single rule for "may sell / is visible to customers" (BR-02, BR-55, UC-61 E1, UC-07 step 2): only ACTIVE stores.
 * T4.3 (opening a batch), T5.1 (search) and T5.2 (holding boxes) must use this instead of checking status themselves.
 */
@Component
class StoreSalesPolicy {
    fun isSellable(store: Store): Boolean = store.status == StoreStatus.ACTIVE

    fun requireSellable(store: Store) {
        if (!isSellable(store)) throw ApiException(409, "STORE_NOT_ACTIVE", "Store is not active")
    }
}
