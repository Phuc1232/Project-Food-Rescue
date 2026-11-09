package com.foodrescue.backend.file.repository

import com.foodrescue.backend.file.entity.StoredFile
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface StoredFileRepository : JpaRepository<StoredFile, UUID>
