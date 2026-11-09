package com.foodrescue.backend.account.repository

import com.foodrescue.backend.account.entity.AccountRole
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AccountRoleRepository : JpaRepository<AccountRole, UUID>
