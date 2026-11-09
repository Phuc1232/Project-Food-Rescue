package com.foodrescue.backend.store.repository

import com.foodrescue.backend.store.entity.Store
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface StoreRepository : JpaRepository<Store, UUID>
