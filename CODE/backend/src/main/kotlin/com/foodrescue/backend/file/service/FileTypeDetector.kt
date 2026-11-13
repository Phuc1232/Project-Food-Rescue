// AC-02: FileTypeDetector responsibility.
package com.foodrescue.backend.file.service

import org.springframework.stereotype.Component

/** Identifies the allowed types from content signatures; client-declared type and file extension are ignored. */
@Component
class FileTypeDetector {
    fun detect(header: ByteArray): String? = when {
        header.startsWith(0xFF, 0xD8, 0xFF) -> "image/jpeg"
        header.startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> "image/png"
        header.size >= 12 && header.startsWith(0x52, 0x49, 0x46, 0x46) &&
            header.copyOfRange(8, 12).contentEquals("WEBP".toByteArray()) -> "image/webp"
        header.startsWith(0x25, 0x50, 0x44, 0x46, 0x2D) -> "application/pdf"
        else -> null
    }

    private fun ByteArray.startsWith(vararg signature: Int): Boolean =
        size >= signature.size && signature.indices.all { this[it] == signature[it].toByte() }

    companion object { const val HEADER_BYTES = 12 }
}
