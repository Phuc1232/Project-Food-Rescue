package com.foodrescue.backend.complaint.repository

import com.foodrescue.backend.complaint.entity.Complaint
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ComplaintRepository : JpaRepository<Complaint, UUID>
