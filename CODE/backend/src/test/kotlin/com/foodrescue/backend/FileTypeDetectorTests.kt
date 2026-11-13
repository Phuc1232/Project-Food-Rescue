// AC-02,06: FileTypeDetectorTests responsibility.
package com.foodrescue.backend

import com.foodrescue.backend.file.service.FileNameSanitizer
import com.foodrescue.backend.file.service.FileTypeDetector
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FileTypeDetectorTests {
    private val detector = FileTypeDetector()
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test fun `recognises the four allowed signatures`() {
        assertEquals("image/jpeg", detector.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0x10)))
        assertEquals("image/png", detector.detect(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0)))
        assertEquals("image/webp", detector.detect("RIFF".toByteArray() + bytes(1, 2, 3, 4) + "WEBPVP8 ".toByteArray()))
        assertEquals("application/pdf", detector.detect("%PDF-1.7\n".toByteArray()))
    }

    @Test fun `rejects everything else including truncated signatures`() {
        val rejected = listOf("<svg xmlns=\"http://www.w3.org/2000/svg\"/>", "<html><script>alert(1)</script>", "GIF89a......",
            "MZ\u0090\u0000 exe", "RIFF\u0000\u0000\u0000\u0000WAVE", "%PDF", "plain text").map { it.toByteArray() } +
            listOf(bytes(0xFF, 0xD8), bytes(0x89, 0x50, 0x4E, 0x47), ByteArray(0))
        rejected.forEach { assertNull(detector.detect(it), String(it)) }
    }

    @Test fun `file names are stripped of paths and control characters and bounded`() {
        val s = FileNameSanitizer()
        assertEquals("passwd", s.sanitize("../../etc/passwd"))
        assertEquals("evil.pdf", s.sanitize("C:\\Users\\x\\evil.pdf"))
        assertEquals("ab.png", s.sanitize("a\u0000b\n.png"))
        assertEquals("Hóa đơn 01.pdf", s.sanitize("  Hóa đơn 01.pdf "))
        assertEquals("file", s.sanitize(null)); assertEquals("file", s.sanitize("../")); assertEquals("file", s.sanitize("  "))
        assertEquals(255, s.sanitize("😀".repeat(300) + ".png").let { it.codePointCount(0, it.length) })
    }
}
