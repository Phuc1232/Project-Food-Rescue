// AC-01,03: ApplicationDocumentValidator responsibility.
package com.foodrescue.backend.store.service

import com.foodrescue.backend.common.response.FieldViolation
import com.foodrescue.backend.file.entity.StoredFile
import com.foodrescue.backend.file.repository.StoredFileRepository
import com.foodrescue.backend.store.ApplicationDocumentType
import com.foodrescue.backend.store.dto.ApplicationDocumentRequest
import org.springframework.stereotype.Component
import java.util.UUID

data class ValidDocument(val type: ApplicationDocumentType, val file: StoredFile)

/** D04 catalogue: known types, required types present, files exist and were uploaded by the applicant. */
@Component
class ApplicationDocumentValidator(private val files: StoredFileRepository) {
    fun validate(documents: List<ApplicationDocumentRequest>, applicantId: UUID): Pair<List<ValidDocument>, List<FieldViolation>> {
        val errors = mutableListOf<FieldViolation>()
        val seen = mutableSetOf<UUID>()
        val valid = documents.mapIndexedNotNull { i, d ->
            val type = ApplicationDocumentType.entries.firstOrNull { it.name == d.type }
            if (type == null) errors += FieldViolation("documents[$i].type", "Unsupported document type")
            val file = d.fileId?.let { id -> files.findById(id).orElse(null)?.takeIf { it.uploadedBy.id == applicantId } }
            when {
                file == null -> errors += FieldViolation("documents[$i].fileId", "File not found")
                !seen.add(file.id!!) -> errors += FieldViolation("documents[$i].fileId", "Duplicate file")
            }
            if (type != null && file != null) ValidDocument(type, file) else null
        }
        val present = documents.mapNotNull { it.type }.toSet()
        ApplicationDocumentType.entries.filter { it.required && it.name !in present }
            .forEach { errors += FieldViolation("documents", "Missing required document: ${it.name}") }
        return valid to errors
    }
}
