// AC-06: FileNameSanitizer responsibility.
package com.foodrescue.backend.file.service

import org.springframework.stereotype.Component

/** Display/download name only: never used to build a storage path. */
@Component
class FileNameSanitizer {
    fun sanitize(original: String?): String {
        val base = original.orEmpty().substringAfterLast('/').substringAfterLast('\\')
        val cleaned = base.codePoints().filter { Character.getType(it) != Character.CONTROL.toInt() }
            .collect(::StringBuilder, StringBuilder::appendCodePoint, StringBuilder::append).toString().trim()
        if (cleaned.isEmpty() || cleaned == "." || cleaned == "..") return "file"
        val count = cleaned.codePointCount(0, cleaned.length)
        return if (count <= MAX) cleaned else cleaned.substring(0, cleaned.offsetByCodePoints(0, MAX))
    }

    private companion object { const val MAX = 255 }
}
