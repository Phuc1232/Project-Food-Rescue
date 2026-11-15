// AC-08 (T4.4): BoxDetail responsibility.
package com.foodrescue.backend.surprisebox.dto

import java.util.UUID

/** UC-33: items with packing-time prices, safety data, packer, inspections and the active allocation's order (if any). */
data class BoxDetail(val box: BoxView, val storageInstructions: String, val safetyWarnings: String, val items: List<BoxItemView>,
                     val inspections: List<BoxInspectionView>, val allocatedOrderId: UUID?)
