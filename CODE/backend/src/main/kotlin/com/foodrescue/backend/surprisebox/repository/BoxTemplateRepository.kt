package com.foodrescue.backend.surprisebox.repository

import com.foodrescue.backend.surprisebox.entity.BoxTemplate
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BoxTemplateRepository : JpaRepository<BoxTemplate, UUID>
