package vn.foodrescue.app.ui.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ReleaseAuthGatewayTest {
    @Test fun `release cannot use preview codes or create simulated sessions`() = runTest {
        val gateway = createAuthGateway()
        assertFalse(gateway.isPreview)
        assertNull(gateway.previewCode("preview-1"))
        assertEquals(AuthReply.Error(AuthIssue.UNAVAILABLE), gateway.login("demo@example.com", "demo password"))
        assertEquals(AuthReply.Error(AuthIssue.UNAVAILABLE), gateway.requestCode("demo@example.com", AuthPurpose.REGISTER))
        assertEquals(AuthReply.Error(AuthIssue.UNAVAILABLE), gateway.verify("preview-1", "246810"))
        assertEquals(AuthReply.Error(AuthIssue.UNAVAILABLE), gateway.resetPassword("reset-preview-1", "long demo passphrase"))
    }
}
