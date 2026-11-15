// AC-03,04,05 (T4.3): SaleBatchAction responsibility.
package com.foodrescue.backend.surprisebox

/** Owner operations of UC-61: PUBLISH DRAFT->OPEN, PAUSE OPEN->PAUSED, RESUME PAUSED->OPEN, END -> ENDED. */
enum class SaleBatchAction { PUBLISH, PAUSE, RESUME, END }
