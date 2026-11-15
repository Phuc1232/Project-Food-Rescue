// AC-03,04,05 (T4.3): SaleBatchStatusRequest responsibility.
package com.foodrescue.backend.surprisebox.dto

import com.foodrescue.backend.surprisebox.SaleBatchAction
import jakarta.validation.constraints.NotNull

data class SaleBatchStatusRequest(@field:NotNull val action: SaleBatchAction?)
