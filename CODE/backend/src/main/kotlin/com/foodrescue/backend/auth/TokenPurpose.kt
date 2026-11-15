// AC-06,13,20,24: TokenPurpose responsibility.
package com.foodrescue.backend.auth

/** SET_PASSWORD (T3.4): first password of a staff account created by a store owner (D01). */
enum class TokenPurpose { VERIFY_EMAIL, RESET_PASSWORD, SET_PASSWORD }
