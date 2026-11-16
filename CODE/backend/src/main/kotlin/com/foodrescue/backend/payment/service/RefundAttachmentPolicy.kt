// AC-06 (T6.5): RefundAttachmentPolicy responsibility.
package com.foodrescue.backend.payment.service

import com.foodrescue.backend.account.PlatformRole
import com.foodrescue.backend.account.repository.AccountRoleRepository
import com.foodrescue.backend.file.entity.Attachment
import com.foodrescue.backend.file.service.AttachmentAccessPolicy
import org.springframework.stereotype.Component
import java.util.UUID

/** Refund evidence: administrators and the customer of the order. */
@Component
class RefundAttachmentPolicy(private val roles: AccountRoleRepository) : AttachmentAccessPolicy {
    override fun supports(attachment: Attachment) = attachment.refund != null
    override fun canRead(accountId: UUID, attachment: Attachment) =
        attachment.refund!!.order.customer.id == accountId || roles.existsByAccountIdAndRole(accountId, PlatformRole.ADMIN)
}
