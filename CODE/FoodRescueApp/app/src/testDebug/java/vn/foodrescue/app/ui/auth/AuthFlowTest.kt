package vn.foodrescue.app.ui.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthFlowTest {
    private val dispatcher = StandardTestDispatcher()
    private val password = "a long demo passphrase"
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun `registration checks confirmation email and whitespace password`() {
        val state = AuthUiState(page = AuthPage.REGISTER, name = "Bao", email = "bad", password = " ".repeat(20), confirmation = "other")
        val errors = AuthValidation.errors(state)
        assertEquals(AuthIssue.EMAIL, errors[AuthFieldKey.EMAIL])
        assertEquals(AuthIssue.PASSWORD_POLICY, errors[AuthFieldKey.PASSWORD])
        assertEquals(AuthIssue.CONFIRMATION, errors[AuthFieldKey.CONFIRMATION])
        assertTrue(AuthValidation.errors(state.copy(email = "demo@example.com", password = password, confirmation = password)).isEmpty())
    }

    @Test fun `old codes are invalid after resend and successful codes cannot be reused`() = runTest(dispatcher) {
        var time = 0L
        val gateway = PreviewAuthGateway({ time }, 0)
        val first = (gateway.requestCode("a@example.com", AuthPurpose.REGISTER) as AuthReply.Challenge).value
        val oldCode = gateway.previewCode(first.id)!!
        assertEquals(AuthReply.Error(AuthIssue.COOLDOWN), gateway.resend(first.id))
        time = 30_000
        val second = (gateway.resend(first.id) as AuthReply.Challenge).value
        assertNotEquals(oldCode, gateway.previewCode(second.id))
        assertEquals(AuthReply.Error(AuthIssue.CODE_EXPIRED), gateway.verify(first.id, oldCode))
        assertEquals(AuthReply.Error(AuthIssue.CODE_WRONG), gateway.verify(second.id, oldCode))
        val code = gateway.previewCode(second.id)!!
        assertEquals(AuthReply.Verified(), gateway.verify(second.id, code))
        assertEquals(AuthReply.Error(AuthIssue.CODE_EXPIRED), gateway.verify(second.id, code))
    }

    @Test fun `codes expire and five failed attempts require a new code`() = runTest(dispatcher) {
        var time = 0L
        val gateway = PreviewAuthGateway({ time }, 0)
        val first = (gateway.requestCode("a@example.com", AuthPurpose.REGISTER) as AuthReply.Challenge).value
        val code = gateway.previewCode(first.id)!!
        repeat(4) { assertEquals(AuthReply.Error(AuthIssue.CODE_WRONG), gateway.verify(first.id, "000000")) }
        assertEquals(AuthReply.Error(AuthIssue.TOO_MANY_ATTEMPTS), gateway.verify(first.id, "000000"))
        assertEquals(AuthReply.Error(AuthIssue.TOO_MANY_ATTEMPTS), gateway.verify(first.id, code))
        time = 30_000
        val next = (gateway.resend(first.id) as AuthReply.Challenge).value
        time = next.expiresAt
        assertEquals(AuthReply.Error(AuthIssue.CODE_EXPIRED), gateway.verify(next.id, gateway.previewCode(next.id)!!))
    }

    @Test fun `password reset requires a reset grant which expires and is single use`() = runTest(dispatcher) {
        var time = 0L
        val gateway = PreviewAuthGateway({ time }, 0)
        assertEquals(AuthReply.Error(AuthIssue.CODE_EXPIRED), gateway.resetPassword("guessed", password))
        val c = (gateway.requestCode("a@example.com", AuthPurpose.RESET_PASSWORD) as AuthReply.Challenge).value
        val grant = (gateway.verify(c.id, gateway.previewCode(c.id)!!) as AuthReply.Verified).resetGrant!!
        assertEquals(AuthReply.Complete, gateway.resetPassword(grant, password))
        assertEquals(AuthReply.Error(AuthIssue.CODE_EXPIRED), gateway.resetPassword(grant, password))
        val next = (gateway.requestCode("a@example.com", AuthPurpose.RESET_PASSWORD) as AuthReply.Challenge).value
        val expiring = (gateway.verify(next.id, gateway.previewCode(next.id)!!) as AuthReply.Verified).resetGrant!!
        time += 300_000
        assertEquals(AuthReply.Error(AuthIssue.CODE_EXPIRED), gateway.resetPassword(expiring, password))
    }

    @Test fun `registration verification cannot grant password reset`() = runTest(dispatcher) {
        val gateway = PreviewAuthGateway({ 0 }, 0)
        val c = (gateway.register("Bao", "a@example.com", password) as AuthReply.Challenge).value
        val result = gateway.verify(c.id, gateway.previewCode(c.id)!!) as AuthReply.Verified
        assertNull(result.resetGrant)
    }

    @Test fun `double submit is ignored and leaving cancels pending navigation`() = runTest(dispatcher) {
        val gateway = PreviewAuthGateway({ testScheduler.currentTime }, 700)
        val vm = AuthViewModel(gateway) { testScheduler.currentTime }
        vm.open(AuthPage.REGISTER)
        vm.edit(AuthFieldKey.NAME, "Bao")
        vm.edit(AuthFieldKey.EMAIL, "demo@example.com")
        vm.edit(AuthFieldKey.PASSWORD, password)
        vm.edit(AuthFieldKey.CONFIRMATION, password)
        vm.submit(); vm.submit()
        assertTrue(vm.state.value.busy)
        advanceTimeBy(701); runCurrent()
        assertEquals("preview-1", vm.state.value.challenge?.id)
        assertEquals("", vm.state.value.password)
        assertEquals("", vm.state.value.confirmation)
        vm.open(AuthPage.LOGIN)
        vm.edit(AuthFieldKey.PASSWORD, password)
        vm.submit(); runCurrent()
        vm.back()
        advanceTimeBy(1000); runCurrent()
        assertEquals(AuthPage.WELCOME, vm.state.value.page)
        assertFalse(vm.state.value.busy)
    }

    @Test fun `reset screen cannot open before verification and clears credentials after completion`() = runTest(dispatcher) {
        val gateway = PreviewAuthGateway({ testScheduler.currentTime }, 0)
        val vm = AuthViewModel(gateway) { testScheduler.currentTime }
        vm.open(AuthPage.NEW_PASSWORD)
        assertEquals(AuthPage.WELCOME, vm.state.value.page)
        vm.open(AuthPage.FORGOT)
        vm.edit(AuthFieldKey.EMAIL, "demo@example.com")
        vm.submit(); runCurrent()
        assertEquals(AuthPage.VERIFY, vm.state.value.page)
        vm.edit(AuthFieldKey.CODE, vm.state.value.previewCode!!)
        vm.submit(); runCurrent()
        assertEquals(AuthPage.NEW_PASSWORD, vm.state.value.page)
        vm.edit(AuthFieldKey.PASSWORD, password)
        vm.edit(AuthFieldKey.CONFIRMATION, password)
        vm.submit(); runCurrent()
        assertEquals(AuthPage.COMPLETE, vm.state.value.page)
        assertNull(vm.state.value.resetGrant)
        assertEquals("", vm.state.value.password)
        vm.open(AuthPage.LOGIN)
        assertEquals("demo@example.com", vm.state.value.email)
    }

    @Test fun `network failure can be retried and unverified login offers a verification flow`() = runTest(dispatcher) {
        val vm = AuthViewModel(PreviewAuthGateway({ testScheduler.currentTime }, 0)) { testScheduler.currentTime }
        vm.open(AuthPage.LOGIN)
        vm.edit(AuthFieldKey.EMAIL, "demo@example.com")
        vm.edit(AuthFieldKey.PASSWORD, password)
        vm.scenario(AuthScenario.NETWORK)
        vm.submit(); runCurrent()
        assertEquals(AuthIssue.NETWORK, vm.state.value.issue)
        assertFalse(vm.state.value.busy)
        vm.scenario(AuthScenario.UNVERIFIED)
        vm.submit(); runCurrent()
        assertTrue(vm.state.value.unverified)
        vm.requestVerification(); runCurrent()
        assertEquals(AuthPurpose.VERIFY_LOGIN, vm.state.value.purpose)
        assertEquals(AuthPage.VERIFY, vm.state.value.page)
        vm.changeEmail()
        assertEquals(AuthPage.LOGIN, vm.state.value.page)
        assertNull(vm.state.value.challenge)
    }
}
