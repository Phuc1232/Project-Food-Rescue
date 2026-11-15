// AC-08 (T4.1): PriceDocumentAttachmentPolicy responsibility.
package com.foodrescue.backend.catalog.service

import com.foodrescue.backend.file.entity.Attachment
import com.foodrescue.backend.file.service.AttachmentAccessPolicy
import org.springframework.stereotype.Component
import java.util.UUID

/** Price documents are readable by whoever may read the store's menu (owner, staff MENU_VIEW, ADMIN). */
@Component
class PriceDocumentAttachmentPolicy(private val access: MenuAccess) : AttachmentAccessPolicy {
    override fun supports(attachment: Attachment) = attachment.priceDocument != null
    override fun canRead(accountId: UUID, attachment: Attachment) = access.canRead(accountId, attachment.priceDocument!!.store.id!!)
}
