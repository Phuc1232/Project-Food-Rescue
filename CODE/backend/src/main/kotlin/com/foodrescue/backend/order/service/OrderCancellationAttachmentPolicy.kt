// AC-01..15 (T7.3): OrderCancellationAttachmentPolicy responsibility.
package com.foodrescue.backend.order.service

import com.foodrescue.backend.account.PlatformRole
import com.foodrescue.backend.account.repository.AccountRoleRepository
import com.foodrescue.backend.file.entity.Attachment
import com.foodrescue.backend.file.service.AttachmentAccessPolicy
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class OrderCancellationAttachmentPolicy(private val roles: AccountRoleRepository) : AttachmentAccessPolicy {
    override fun supports(attachment: Attachment) = attachment.orderCancellationRequest != null
    override fun canRead(accountId: UUID, attachment: Attachment): Boolean {
        val order = attachment.orderCancellationRequest!!.order
        return order.customer.id == accountId || order.store.owner.id == accountId || roles.existsByAccountIdAndRole(accountId, PlatformRole.ADMIN)
    }
}
