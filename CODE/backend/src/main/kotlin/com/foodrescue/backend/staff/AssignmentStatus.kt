// AC-03,07 (T3.4): AssignmentStatus responsibility.
package com.foodrescue.backend.staff

/** INVITED: waiting for the invited account to accept (no store context yet). Only ACTIVE grants permissions. */
enum class AssignmentStatus { INVITED, ACTIVE, DECLINED, REVOKED }
