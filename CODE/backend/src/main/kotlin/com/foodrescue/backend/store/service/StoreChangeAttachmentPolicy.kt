// AC-07,08: StoreChangeAttachmentPolicy responsibility.
package com.foodrescue.backend.store.service

import com.foodrescue.backend.account.PlatformRole
import com.foodrescue.backend.account.repository.AccountRoleRepository
import com.foodrescue.backend.file.entity.Attachment
import com.foodrescue.backend.file.service.AttachmentAccessPolicy
import org.springframework.stereotype.Component
import java.util.UUID

/** Documents of a store change request: the store owner and administrators. */
@Component
class StoreChangeAttachmentPolicy(private val roles: AccountRoleRepository) : AttachmentAccessPolicy {
    override fun supports(attachment: Attachment) = attachment.storeChangeRequest != null

    override fun canRead(accountId: UUID, attachment: Attachment) =
        attachment.storeChangeRequest!!.store.owner.id == accountId || roles.existsByAccountIdAndRole(accountId, PlatformRole.ADMIN)
}
