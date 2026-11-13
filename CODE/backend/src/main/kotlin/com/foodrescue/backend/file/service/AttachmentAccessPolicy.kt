// AC-12: AttachmentAccessPolicy responsibility.
package com.foodrescue.backend.file.service

import com.foodrescue.backend.file.entity.Attachment
import java.util.UUID

/**
 * Extension point: the task that owns an attachment target (partner application, complaint, refund, ...) registers
 * a bean deciding who may read files attached to it. Until one exists, [FileAccessService] falls back to uploader + ADMIN.
 */
interface AttachmentAccessPolicy {
    fun supports(attachment: Attachment): Boolean
    fun canRead(accountId: UUID, attachment: Attachment): Boolean
}
