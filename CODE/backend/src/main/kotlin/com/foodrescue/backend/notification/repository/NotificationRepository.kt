package com.foodrescue.backend.notification.repository

import com.foodrescue.backend.notification.entity.Notification
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface NotificationRepository : JpaRepository<Notification, UUID>
