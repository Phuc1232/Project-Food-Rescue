package com.foodrescue.backend.store.repository

import com.foodrescue.backend.store.entity.StoreOpeningHour
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface StoreOpeningHourRepository : JpaRepository<StoreOpeningHour, UUID>
