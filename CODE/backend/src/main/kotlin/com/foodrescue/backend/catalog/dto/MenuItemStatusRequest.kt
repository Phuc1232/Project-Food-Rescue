// AC-06 (T4.1): MenuItemStatusRequest responsibility.
package com.foodrescue.backend.catalog.dto

import com.foodrescue.backend.catalog.MenuItemStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class MenuItemStatusRequest(@field:NotNull val version: Long?, @field:NotNull val status: MenuItemStatus?,
                                 @field:NotBlank @field:Size(max = 1000) val reason: String?)
