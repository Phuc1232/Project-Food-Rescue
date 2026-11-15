package com.foodrescue.app.feature.finance

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import com.foodrescue.app.feature.complaint.data.LocalComplaintHolds
import com.foodrescue.app.feature.order.data.LocalOrderLedger
import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.payment.domain.*
import com.foodrescue.app.feature.finance.data.*
import com.foodrescue.app.feature.finance.domain.*
import com.foodrescue.app.feature.store.data.LocalOwnerStoreRepository
import com.foodrescue.app.feature.store.domain.StorePayoutAccount
import com.foodrescue.app.feature.surprisebox.domain.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class RevenuePersistenceTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "revenue_qa_${UUID.randomUUID()}_"
    private val names = mutableSetOf<String>()
    private val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            names += prefix + name; return base.getSharedPreferences(prefix + name, mode)
        }
    }
    private val now = 1_800_000_000_000L
    private val account = LocalOrderLedger.accountKey("customer@example.com")
    private val owner = LocalOwnerStoreRepository(context, "owner@foodrescue.vn")
    private val ledger = LocalOrderLedger(context)
    private val bank = StorePayoutAccount("Vietcombank", "NGUYEN AN", "0123456789")
    private fun order(id: String = "one", store: Int = 7) = BoxReservation(id, -10, 2,
        PickupWindow(now - 1000, now + 1000), now - 3000, now - 2000, 49000,
        QrPayment("FR$id", 98000, PaymentStatus.SUCCEEDED, now - 2500), collectedAtMillis = now - 500,
        boxSnapshot = CatalogBox(-10, "Tiệm Bánh An Nhiên", BoxCategory.BAKERY, "Hộp bánh cuối ngày", 49000, 120000, 5,
            "17:00 – 19:00", "Bánh tươi", "Sữa", "Bàn Cờ", 0, 17, "AN", storeId = store, batchId = "batch"))
    private fun repository(email: String = "owner@foodrescue.vn") = LocalRevenueRepository(7,
        LocalOwnerStoreRepository(context, email), ledger, LocalSettlementStore(context), LocalComplaintHolds(context), 2000) { now }
    private fun saveOrders(vararg orders: BoxReservation) { ledger.write(orders.map { LocalOrderLedger.Row(account, it) }) }
    private fun settlement(store: Int = 7): JSONObject {
        val period = RevenuePeriod.WEEK.window(now)
        return JSONObject().put("id", "DS-001").put("storeId", store).put("periodStart", period.start).put("periodEnd", period.endExclusive)
            .put("status", "PAID").put("updatedAt", now).put("paidAt", now).put("transferReference", "VNP-123")
            .put("destination", JSONObject().put("bank", bank.bank).put("holder", bank.holder).put("lastFour", "6789"))
            .put("lines", JSONArray().put(JSONObject().put("orderId", "one").put("reference", "FRone").put("boxName", "Hộp bánh cuối ngày")
                .put("quantity", 2).put("gross", 98000).put("commissionBps", 1500).put("commission", 14700)))
    }
    private fun saveRecords(vararg records: JSONObject) {
        val root = JSONObject().put("version", 1).put("records", JSONArray().apply { records.forEach { put(it) } })
        check(context.getSharedPreferences("owner_settlements.v1", 0).edit().putString("store.7", root.toString()).commit())
    }
    @After fun cleanup() { names.forEach { base.deleteSharedPreferences(it) } }

    @Test fun reportReadsOnlyThisStoresOrdersAndNeverWritesTheLedger() = runBlocking {
        saveOrders(order(), order("foreign", 8))
        val before = context.getSharedPreferences("commerce_orders.v2", 0).all.toMap()
        val value = repository().load()
        assertEquals(listOf("one"), value.entries.map { it.order.id }); assertEquals(78400L, value.awaitingPayout)
        assertEquals(before, context.getSharedPreferences("commerce_orders.v2", 0).all)
        assertEquals(78400L, repository().load().awaitingPayout)
    }
    @Test fun unapprovedOwnerCannotReadTheReport() = runBlocking {
        saveOrders(order())
        try { repository("new-owner@example.com").load(); fail("Expected denied access") } catch (_: IllegalStateException) { }
    }
    @Test fun openComplaintSuppressesPendingSettlementAndCorruptionFailsClosed() = runBlocking {
        saveOrders(order())
        val preferences = context.getSharedPreferences("customer_complaints_$account", 0)
        val cases = JSONArray().put(JSONObject().put("order", JSONObject().put("id", "one")).put("status", "PENDING"))
        check(preferences.edit().putString("cases.v1", cases.toString()).commit())
        assertEquals(RevenueEntryStatus.HELD, repository().load().entries.single().status)
        assertTrue(repository().load().settlements.isEmpty())
        check(preferences.edit().putString("cases.v1", "broken").commit())
        try { repository().load(); fail("Corrupt holds must fail") } catch (_: org.json.JSONException) { }
        assertEquals("broken", preferences.getString("cases.v1", null))
    }
    @Test fun paidSnapshotSurvivesRestartAndBankChangesWithItsOriginalFee() = runBlocking {
        saveOrders(order()); saveRecords(settlement())
        owner.updatePayout(StorePayoutAccount("Techcombank", "NGUYEN AN", "9876543210"))
        val value = repository().load()
        assertEquals(83300L, value.paidOut); assertEquals(0L, value.awaitingPayout)
        assertEquals("6789", value.settlements.single().destination!!.lastFour)
        assertEquals("9876543210", value.payout!!.number)
        assertEquals(14700L, value.entries.single().commission)
    }
    @Test fun foreignStoreAndDuplicateRecordsAreRejectedWithoutOverwrite() = runBlocking {
        saveOrders(order()); saveRecords(settlement(8))
        try { repository().load(); fail() } catch (_: IllegalArgumentException) { }
        saveRecords(settlement(), settlement().put("id", "duplicate"))
        val before = context.getSharedPreferences("owner_settlements.v1", 0).all.toMap()
        try { repository().load(); fail() } catch (_: IllegalArgumentException) { }
        assertEquals(before, context.getSharedPreferences("owner_settlements.v1", 0).all)
    }
    @Test fun pendingGroupIsReplacedByConfirmedRecordWithoutDuplicateMoney() = runBlocking {
        saveOrders(order()); assertEquals(SettlementStatus.PENDING, repository().load().settlements.single().status)
        saveRecords(settlement())
        val after = repository().load()
        assertEquals(1, after.settlements.size); assertEquals(0L, after.awaitingPayout); assertEquals(83300L, after.paidOut)
    }
    @Test fun malformedSettlementDoesNotBecomeAnEmptySuccessfulReport() = runBlocking {
        val preferences = context.getSharedPreferences("owner_settlements.v1", 0)
        check(preferences.edit().putString("store.7", "{bad}").commit())
        try { repository().load(); fail() } catch (_: org.json.JSONException) { }
        assertEquals("{bad}", preferences.getString("store.7", null))
    }
}
