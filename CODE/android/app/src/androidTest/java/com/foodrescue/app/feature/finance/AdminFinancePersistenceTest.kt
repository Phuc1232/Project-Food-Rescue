package com.foodrescue.app.feature.finance

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import com.foodrescue.app.feature.account.data.*
import com.foodrescue.app.feature.account.domain.AccountRole
import com.foodrescue.app.feature.account.presentation.primaryRole
import com.foodrescue.app.feature.finance.data.*
import com.foodrescue.app.feature.finance.domain.*
import com.foodrescue.app.feature.order.data.*
import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.payment.domain.*
import com.foodrescue.app.feature.refund.data.LocalAdminRefundRepository
import com.foodrescue.app.feature.refund.domain.*
import com.foodrescue.app.feature.store.data.*
import com.foodrescue.app.feature.store.domain.*
import com.foodrescue.app.feature.surprisebox.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class AdminFinancePersistenceTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val names = mutableSetOf<String>()
    private val prefix = "admin_finance_test_${UUID.randomUUID()}_"
    private var failWrite: String? = null
    private val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            names += prefix + name
            val prefs = base.getSharedPreferences(prefix + name, mode)
            return object : SharedPreferences by prefs {
                override fun edit(): SharedPreferences.Editor {
                    val editor = prefs.edit()
                    return object : SharedPreferences.Editor by editor {
                        override fun putString(key: String?, value: String?): SharedPreferences.Editor { editor.putString(key, value); return this }
                        override fun commit(): Boolean {
                            val success = editor.commit()
                            if (failWrite == name) { failWrite = null; return false }
                            return success
                        }
                    }
                }
            }
        }
    }
    private var now = 1_800_000_000_000L
    private val actor = "admin@foodrescue.vn"
    private val ownerEmail = "finance-owner@example.com"
    private val key = LocalStoreRecords.accountKey(ownerEmail)
    private val account = LocalOrderLedger.accountKey("customer@example.com")
    private val bank = StorePayoutAccount("Vietcombank", "NGUYEN AN", "0123456789")
    private val storeId = LocalPublicStoreRepository(context).register(key)
    private val ledger = LocalOrderLedger(context)
    private val records = LocalSettlementStore(context)
    private val reason = "Đã kiểm tra đầy đủ số tiền và tài khoản."
    private fun repository(email: String = actor) = LocalAdminSettlementRepository(context, email) { now }
    private fun store() { LocalStoreRecords(context).change(key, { OwnerStore() }) {
        it.copy(profile = OwnerStoreProfile(name = "Tiệm An"), status = StoreReviewStatus.APPROVED, payout = bank)
    } }
    private fun order(id: String = "one") = BoxReservation(id, -10, 1, PickupWindow(now - 1000, now + 1000), now - 4000, now - 2000,
        100000, QrPayment("FR$id", 100000, PaymentStatus.SUCCEEDED, now - 3000), collectedAtMillis = now - 500,
        boxSnapshot = CatalogBox(-10, "Tiệm An", BoxCategory.BAKERY, "Hộp bánh", 100000, 200000, 5,
            "17:00 – 19:00", "Bánh tươi", "Sữa", "Bàn Cờ", 0, 17, "AN", storeId = storeId, batchId = "batch"))
    private fun save(vararg orders: BoxReservation) = ledger.write(orders.map { LocalOrderLedger.Row(account, it) })
    private suspend fun one() = repository().load().settlements.single()
    private suspend fun act(item: ManagedSettlement, action: SettlementAction, reference: String = "") =
        repository().decide(item, SettlementDecision(action, reason, reference))
    @After fun cleanup() { names.forEach { base.deleteSharedPreferences(it) } }

    @Test fun adminAndOwnerShareTheSameConfirmedAndPaidRecord() = runBlocking {
        store(); save(order())
        val pending = one(); val ready = act(pending, SettlementAction.CONFIRM)
        assertEquals(1, records.read(storeId).size)
        val processing = act(one(), SettlementAction.START)
        val paid = act(processing, SettlementAction.COMPLETE, "BANK-000001")
        val owner = LocalRevenueRepository(storeId, LocalOwnerStoreRepository(context, ownerEmail), ledger, records,
            com.foodrescue.app.feature.complaint.data.LocalComplaintHolds(context), 2000) { now }.load()
        assertEquals(80000L, owner.paidOut); assertEquals(0L, owner.awaitingPayout)
        assertEquals(ready.record.id, paid.record.id); assertEquals(paid.record, owner.settlements.single())
        assertEquals(3, owner.settlements.single().history.size)
    }
    @Test fun staleAndUnauthorizedWritesDoNotCreateDuplicateMoney() = runBlocking {
        store(); save(order()); val pending = one()
        act(pending, SettlementAction.CONFIRM)
        assertTrue(runCatching { act(pending, SettlementAction.CONFIRM) }.exceptionOrNull() is StaleFinanceData)
        assertTrue(runCatching { repository("customer@example.com").load() }.isFailure)
        assertEquals(1, records.read(storeId).size)
    }
    @Test fun changingTheBankAfterConfirmationKeepsThePaymentDestination() = runBlocking {
        store(); save(order()); act(one(), SettlementAction.CONFIRM)
        LocalStoreRecords(context).change(key, { error("missing") }) { it.copy(payout = bank.copy(number = "9999999999")) }
        val ready = one(); assertEquals("0123456789", ready.record.destination!!.accountNumber)
        act(ready, SettlementAction.START)
        assertEquals("6789", one().record.destination!!.lastFour)
    }
    @Test fun concurrentBankChangeRequiresReviewingThePendingPeriodAgain() = runBlocking {
        store(); save(order()); val old = one()
        LocalStoreRecords(context).change(key, { error("missing") }) { it.copy(payout = bank.copy(number = "9999999999")) }
        assertTrue(runCatching { act(old, SettlementAction.CONFIRM) }.exceptionOrNull() is StaleFinanceData)
        assertTrue(records.read(storeId).isEmpty())
    }
    @Test fun refundBeforeTransferBlocksPaymentAndVoidRebuildsCorrectNet() = runBlocking {
        store(); val order = order(); save(order); act(one(), SettlementAction.CONFIRM)
        val refund = ManagedRefund("refund", RefundSource.COMPLAINT, "case", reason, 20000, now,
            AdminRefundStatus.COMPLETED, listOf(RefundEvent(AdminRefundStatus.COMPLETED, actor, now, reason)))
        save(order.copy(adminRefunds = listOf(refund)))
        val changed = one(); assertTrue(SettlementIssue.ORDER_CHANGED in changed.issues)
        assertFalse(SettlementAction.START in changed.actions)
        act(changed, SettlementAction.VOID)
        val pending = repository().load().settlements.single { it.record.status == SettlementStatus.PENDING }
        assertEquals(64000L, pending.record.net)
    }
    @Test fun failedDiskCommitRestoresPreviousSettlementRecord() = runBlocking {
        store(); save(order()); val pending = one()
        failWrite = "owner_settlements.v1"
        assertTrue(runCatching { act(pending, SettlementAction.CONFIRM) }.isFailure)
        assertTrue(records.read(storeId).isEmpty())
        assertEquals(SettlementStatus.PENDING, one().record.status)
    }
    @Test fun policySurvivesRestartAndFailedCommitDoesNotAdvanceRevision() = runBlocking {
        val repository = LocalFinancialPolicyRepository(context, actor) { now }
        repository.publish(0, FinancialTerms(commissionBps = 1500), 0, reason)
        assertEquals(1500, LocalFinancialPolicyStore(context).at(now).commissionBps)
        now += 1000
        failWrite = "financial_policy.v1"
        assertTrue(runCatching { repository.publish(1, FinancialTerms(commissionBps = 1000), 0, reason) }.isFailure)
        assertEquals(1L, repository.load().revision)
        assertTrue(runCatching { LocalFinancialPolicyRepository(context, "customer@example.com").publish(1, FinancialTerms(), 0, reason) }.isFailure)
    }
    @Test fun orderSerializationPreservesTheAcceptedPolicy() {
        val value = order().copy(financialTerms = FinancialTerms(3, 1525, 30, 7500, 15, 2500))
        assertEquals(value, OrderJson.read(OrderJson.write(value)))
        val old = OrderJson.write(value).apply { remove("financialTerms") }
        assertNull(OrderJson.read(old).financialTerms)
    }
    @Test fun noShowPolicyCreatesOneRefundRequestAndNeverCollectsCash() = runBlocking {
        val value = order().copy(collectedAtMillis = null, pickup = PickupWindow(now - 1000, now - 1),
            financialTerms = FinancialTerms(noShowRefundBps = 2500),
            noShow = NoShowReceipt("request", emptyList(), "", "staff", "An", now))
        save(value)
        val refunds = LocalAdminRefundRepository(context, actor, RefundProcessor { _, _, _ -> RefundResult.COMPLETED }, { now })
        val request = refunds.load().single()
        assertEquals(25000L, request.amount); assertEquals(RefundSource.NO_SHOW_POLICY, request.source)
        refunds.decide(request, true, reason); now += 3000
        val completed = refunds.load().single()
        assertEquals(AdminRefundStatus.COMPLETED, completed.status)
        assertEquals(1, refunds.load().size)
        assertEquals(25000L, ledger.read().single().order.completedRefundAmount)
    }
    @Test fun legacyCustomersAppearWithoutInventedEmailOrMutatingOrders() = runBlocking {
        val legacy = LocalOrderHistoryStore(context, "old-customer@example.com")
        val key = LocalOrderLedger.accountKey("old-customer@example.com")
        legacy.save(listOf(order().copy(boxSnapshot = null)))
        val before = context.getSharedPreferences("customer_orders_$key", 0).all.toMap()
        val admin = LocalAccountManagementRepository(context, actor, LocalLegacyAccountHistory(context) { listOf(key) }) { now }
        val customer = admin.accounts().single { it.key == key }
        assertEquals(AccountRole.CUSTOMER, customer.primaryRole); assertNull(customer.email); assertEquals(1, customer.orderCount)
        assertEquals(before, context.getSharedPreferences("customer_orders_$key", 0).all)
        ledger.write(listOf(LocalOrderLedger.Row(key, order().copy(boxSnapshot = null))))
        assertEquals(1, admin.accounts().single { it.key == key }.orderCount)
    }
    @Test fun duplicateBankReferencesAreRejectedAcrossSettlements() = runBlocking {
        store(); save(order()); act(one(), SettlementAction.CONFIRM)
        val processing = act(one(), SettlementAction.START); act(processing, SettlementAction.COMPLETE, "BANK-000001")
        save(order(), order("two"))
        val pending = repository().load().settlements.single { it.record.status == SettlementStatus.PENDING }
        val ready = act(pending, SettlementAction.CONFIRM)
        val next = act(ready, SettlementAction.START)
        assertTrue(runCatching { act(next, SettlementAction.COMPLETE, "bank-000001") }.isFailure)
        assertEquals(80000L, repository().load().settlements.filter { it.record.status == SettlementStatus.PAID }.sumOf { it.record.net })
    }
}
