package com.foodrescue.app.app.navigation

/** Routing only; backend authorization is still required. */
object RoleNavigation {
    enum class Role { CUSTOMER, OWNER, STAFF, ADMIN }

    fun destinationFor(role: Role?): Destinations = when (role) {
        null -> Destinations.AUTH
        Role.CUSTOMER -> Destinations.CUSTOMER
        Role.OWNER -> Destinations.OWNER
        Role.STAFF -> Destinations.STAFF
        Role.ADMIN -> Destinations.ADMIN
    }
}
