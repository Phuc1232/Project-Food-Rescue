package com.foodrescue.backend.store.repository

import com.foodrescue.backend.store.entity.PartnerApplication
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PartnerApplicationRepository : JpaRepository<PartnerApplication, UUID>
