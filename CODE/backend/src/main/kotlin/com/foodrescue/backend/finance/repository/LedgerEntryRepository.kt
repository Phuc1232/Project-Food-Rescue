package com.foodrescue.backend.finance.repository

import com.foodrescue.backend.finance.entity.LedgerEntry
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface LedgerEntryRepository : JpaRepository<LedgerEntry, UUID>
