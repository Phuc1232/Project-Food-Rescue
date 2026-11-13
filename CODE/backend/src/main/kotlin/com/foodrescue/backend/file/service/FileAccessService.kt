// AC-04,12: FileAccessService responsibility.
package com.foodrescue.backend.file.service

import com.foodrescue.backend.account.PlatformRole
import com.foodrescue.backend.account.repository.AccountRoleRepository
import com.foodrescue.backend.file.entity.StoredFile
import com.foodrescue.backend.file.repository.AttachmentRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class FileAccessService(
    private val attachments: AttachmentRepository,
    private val policies: List<AttachmentAccessPolicy>,
    private val roles: AccountRoleRepository
) {
    /** Unattached files: uploader only. Attached files: owning policy, otherwise uploader or ADMIN. */
    fun canRead(accountId: UUID, file: StoredFile): Boolean {
        val uploader = file.uploadedBy.id == accountId
        val links = attachments.findAllByFileId(file.id!!)
        if (links.isEmpty()) return uploader
        if (links.any { link -> policies.firstOrNull { it.supports(link) }?.canRead(accountId, link) == true }) return true
        return uploader || roles.existsByAccountIdAndRole(accountId, PlatformRole.ADMIN)
    }
}
