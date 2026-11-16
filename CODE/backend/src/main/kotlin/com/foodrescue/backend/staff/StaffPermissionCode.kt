// AC-04,06,10 (T3.4): StaffPermissionCode responsibility.
package com.foodrescue.backend.staff

/** Staff-grantable permissions (seeded by V7). There is no price, commission, policy, owner or admin permission (BR-03). */
enum class StaffPermissionCode { MENU_VIEW, BOX_PACK, BOX_MANAGE, PICKUP_HANDLE, ORDER_VIEW, NO_SHOW_CONFIRM }
