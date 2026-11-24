// AC-01..12 (T7.5): BoxAttachmentPolicy responsibility.
package com.foodrescue.backend.surprisebox.service
import com.foodrescue.backend.account.PlatformRole
import com.foodrescue.backend.account.repository.AccountRoleRepository
import com.foodrescue.backend.common.security.StoreAuthorizationService
import com.foodrescue.backend.file.entity.Attachment
import com.foodrescue.backend.file.service.AttachmentAccessPolicy
import org.springframework.stereotype.Component
import java.util.UUID
@Component
class BoxAttachmentPolicy(private val roles: AccountRoleRepository, private val authorization: StoreAuthorizationService) : AttachmentAccessPolicy {
    override fun supports(attachment: Attachment) = attachment.box != null
    override fun canRead(accountId: UUID, attachment: Attachment): Boolean {
        val storeId = attachment.box!!.store.id
        return roles.existsByAccountIdAndRole(accountId, PlatformRole.ADMIN) || authorization.contexts(accountId).any {
            it.storeId == storeId && (it.owner || "BOX_MANAGE" in it.permissions || "BOX_PACK" in it.permissions)
        }
    }
}
