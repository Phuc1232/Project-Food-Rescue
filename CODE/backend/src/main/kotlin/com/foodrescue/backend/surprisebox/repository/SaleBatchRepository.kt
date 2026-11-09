package com.foodrescue.backend.surprisebox.repository

import com.foodrescue.backend.surprisebox.entity.SaleBatch
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SaleBatchRepository : JpaRepository<SaleBatch, UUID>
