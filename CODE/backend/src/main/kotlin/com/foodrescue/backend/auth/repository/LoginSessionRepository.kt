package com.foodrescue.backend.auth.repository

import com.foodrescue.backend.auth.entity.LoginSession
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface LoginSessionRepository : JpaRepository<LoginSession, UUID>
