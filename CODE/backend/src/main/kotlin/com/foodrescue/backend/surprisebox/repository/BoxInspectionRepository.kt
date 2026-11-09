package com.foodrescue.backend.surprisebox.repository

import com.foodrescue.backend.surprisebox.entity.BoxInspection
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BoxInspectionRepository : JpaRepository<BoxInspection, UUID>
