// AC-01,03: ApplicationDocumentType responsibility.
package com.foodrescue.backend.store

/** Document catalogue D04 (T3.2 Gate A Q4). */
enum class ApplicationDocumentType(val required: Boolean) {
    BUSINESS_REGISTRATION(true), FOOD_SAFETY(true), STOREFRONT_PHOTO(false), OTHER(false)
}
