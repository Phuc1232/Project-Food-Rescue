package com.foodrescue.backend.file.repository

import com.foodrescue.backend.file.entity.Attachment
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AttachmentRepository : JpaRepository<Attachment, UUID>
