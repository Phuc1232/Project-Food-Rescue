// AC-09,10: StoreStatusAction responsibility.
package com.foodrescue.backend.store

/** Admin-only store status transitions (UC-77): ACTIVE -> SUSPENDED and back. */
enum class StoreStatusAction { SUSPEND, REACTIVATE }
