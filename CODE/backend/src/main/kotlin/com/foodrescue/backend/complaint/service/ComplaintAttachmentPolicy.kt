// AC-01..12 (T7.5): ComplaintAttachmentPolicy responsibility.
package com.foodrescue.backend.complaint.service
import com.foodrescue.backend.account.PlatformRole
import com.foodrescue.backend.account.repository.AccountRoleRepository
import com.foodrescue.backend.common.security.StoreAuthorizationService
import com.foodrescue.backend.file.entity.Attachment
import com.foodrescue.backend.file.service.AttachmentAccessPolicy
import org.springframework.stereotype.Component
import java.util.UUID
@Component
class ComplaintAttachmentPolicy(private val roles: AccountRoleRepository) : AttachmentAccessPolicy {
    override fun supports(attachment: Attachment) = attachment.complaint != null
    override fun canRead(accountId: UUID, attachment: Attachment): Boolean {
        val complaint = attachment.complaint!!
        return complaint.customer.id == accountId || complaint.store.owner.id == accountId || roles.existsByAccountIdAndRole(accountId, PlatformRole.ADMIN)
    }
}
