// AC-01..14 (T7.4): NoShowAttachmentPolicy responsibility.
package com.foodrescue.backend.pickup.service
import com.foodrescue.backend.account.PlatformRole
import com.foodrescue.backend.account.repository.AccountRoleRepository
import com.foodrescue.backend.common.security.StoreAuthorizationService
import com.foodrescue.backend.file.entity.Attachment
import com.foodrescue.backend.file.service.AttachmentAccessPolicy
import org.springframework.stereotype.Component
import java.util.UUID
@Component
class NoShowAttachmentPolicy(private val roles: AccountRoleRepository, private val authorization: StoreAuthorizationService) : AttachmentAccessPolicy {
    override fun supports(attachment: Attachment) = attachment.noShowCase != null
    override fun canRead(accountId: UUID, attachment: Attachment): Boolean {
        val order = attachment.noShowCase!!.order
        return order.customer.id == accountId || roles.existsByAccountIdAndRole(accountId, PlatformRole.ADMIN) ||
            authorization.contexts(accountId).any { it.storeId == order.store.id && (it.owner || "NO_SHOW_CONFIRM" in it.permissions) }
    }
}
