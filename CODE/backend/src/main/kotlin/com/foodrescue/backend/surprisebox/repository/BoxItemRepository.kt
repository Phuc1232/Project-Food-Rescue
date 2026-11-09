package com.foodrescue.backend.surprisebox.repository

import com.foodrescue.backend.surprisebox.entity.BoxItem
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BoxItemRepository : JpaRepository<BoxItem, UUID>
