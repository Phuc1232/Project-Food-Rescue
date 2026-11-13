// AC-06,07: PartnerApplicationAttachmentPolicy responsibility.
package com.foodrescue.backend.store.service

import com.foodrescue.backend.account.PlatformRole
import com.foodrescue.backend.account.repository.AccountRoleRepository
import com.foodrescue.backend.file.entity.Attachment
import com.foodrescue.backend.file.service.AttachmentAccessPolicy
import org.springframework.stereotype.Component
import java.util.UUID

/** Partner application documents: the applicant and administrators (T3.2 BR-T32-09). */
@Component
class PartnerApplicationAttachmentPolicy(private val roles: AccountRoleRepository) : AttachmentAccessPolicy {
    override fun supports(attachment: Attachment) = attachment.partnerApplication != null

    override fun canRead(accountId: UUID, attachment: Attachment) =
        attachment.partnerApplication!!.applicant.id == accountId || roles.existsByAccountIdAndRole(accountId, PlatformRole.ADMIN)
}
