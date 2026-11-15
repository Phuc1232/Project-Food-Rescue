// AC-01,05,07,08 (T4.4): BoxItemRepository responsibility.
package com.foodrescue.backend.surprisebox.repository

import com.foodrescue.backend.surprisebox.entity.BoxItem
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BoxItemRepository : JpaRepository<BoxItem, UUID> {
    fun findAllByBoxIdOrderByItemNameSnapshot(boxId: UUID): List<BoxItem>
}
