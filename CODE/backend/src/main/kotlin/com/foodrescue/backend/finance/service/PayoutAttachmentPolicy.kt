// AC-01..12 (T8.4): PayoutAttachmentPolicy responsibility.
package com.foodrescue.backend.finance.service
import com.foodrescue.backend.account.PlatformRole
import com.foodrescue.backend.account.repository.AccountRoleRepository
import com.foodrescue.backend.file.entity.Attachment
import com.foodrescue.backend.file.service.AttachmentAccessPolicy
import org.springframework.stereotype.Component
import java.util.UUID
@Component
class PayoutAttachmentPolicy(private val roles: AccountRoleRepository): AttachmentAccessPolicy {
    override fun supports(attachment: Attachment)=attachment.payout!=null
    override fun canRead(accountId: UUID,attachment: Attachment)=attachment.payout!!.store.owner.id==accountId || roles.existsByAccountIdAndRole(accountId,PlatformRole.ADMIN)
}
