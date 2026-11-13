package com.foodrescue.backend

import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.catalog.entity.MenuItem
import com.foodrescue.backend.common.persistence.BaseEntity
import com.foodrescue.backend.order.entity.CustomerOrder
import com.foodrescue.backend.store.entity.Store
import com.foodrescue.backend.surprisebox.entity.BoxItem
import com.foodrescue.backend.surprisebox.entity.SaleBatch
import jakarta.persistence.EntityManager
import org.hibernate.Hibernate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

@SpringBootTest
@Testcontainers
class FoodRescueApplicationTests {
    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")

        @DynamicPropertySource
        @JvmStatic
        fun database(registry: DynamicPropertyRegistry) {
            com.foodrescue.backend.support.TestAuthKeys.configure(registry)
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Autowired lateinit var entityManager: EntityManager
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var transactionManager: PlatformTransactionManager
    private val ids = mutableMapOf<String, UUID>()
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private fun <T> tx(action: () -> T): T? = TransactionTemplate(transactionManager).execute { action() }

    @BeforeEach
    fun fixtures() {
        val tables = jdbc.queryForList("select tablename from pg_tables where schemaname = 'public' and tablename <> 'flyway_schema_history'", String::class.java)
        jdbc.execute("TRUNCATE " + tables.joinToString(", ") { "\"$it\"" } + " CASCADE")
        ids.clear()
        tx {
            val instances = mutableMapOf<KClass<*>, BaseEntity>()
            fun create(type: KClass<*>): BaseEntity {
                instances[type]?.let { return it }
                val constructor = requireNotNull(type.primaryConstructor)
                val values = constructor.parameters.associateWith { parameter ->
                    val field = parameter.name!!
                    val classifier = parameter.type.classifier as KClass<*>
                    when {
                        // Exactly one typed attachment owner is required.
                        type.simpleName == "Attachment" && field == "complaint" -> create(Class.forName("com.foodrescue.backend.complaint.entity.Complaint").kotlin)
                        type.simpleName == "Payment" && field == "order" -> create(CustomerOrder::class)
                        type.simpleName == "FinancialEvent" && field == "payment" -> create(Class.forName("com.foodrescue.backend.payment.entity.Payment").kotlin)
                        type.simpleName in setOf("RefreshToken","AccountActionToken") && field == "expiresAt" -> Instant.now().plusSeconds(600)
                        parameter.type.isMarkedNullable -> null
                        BaseEntity::class.java.isAssignableFrom(classifier.java) -> create(classifier)
                        classifier.java.isEnum -> classifier.java.enumConstants.first()
                        classifier == String::class -> if (field == "currency") "VND" else field.lowercase()
                        classifier == Int::class -> if (field == "disputeWindowHours") 48 else 1
                        classifier == Long::class -> 1L
                        classifier == Boolean::class -> false
                        classifier == UUID::class -> UUID.randomUUID()
                        classifier == BigDecimal::class -> when {
                            field.endsWith("Rate") -> BigDecimal("0.30")
                            field == "latitude" || field == "longitude" -> BigDecimal("10")
                            else -> BigDecimal("100000")
                        }
                        classifier == LocalDate::class -> LocalDate.of(2026, 1, 1)
                        classifier == LocalTime::class -> if (field == "opensAt") LocalTime.of(8, 0) else LocalTime.of(20, 0)
                        classifier == Instant::class -> when (field) {
                            "holdExpiresAt", "expiresAt", "orderDeadline" -> now.plusSeconds(600)
                            "pickupStartsAt", "periodStartsAt" -> now.plusSeconds(1800)
                            "pickupEndsAt", "periodEndsAt" -> now.plusSeconds(3600)
                            "safeUntil" -> now.plusSeconds(7200)
                            else -> now
                        }
                        else -> error("Missing fixture value for $type.$field")
                    }
                }
                val entity = constructor.callBy(values) as BaseEntity
                entityManager.persist(entity)
                instances[type] = entity
                ids[type.simpleName!!] = requireNotNull(entity.id)
                return entity
            }
            entityManager.metamodel.entities.forEach { create(it.javaType.kotlin) }
            entityManager.flush()
            entityManager.clear()
            entityManager.metamodel.entities.forEach {
                assertNotNull(entityManager.find(it.javaType, ids.getValue(it.javaType.simpleName)))
            }
        }
    }

    @Test
    fun `all entities persist and load against migrated PostgreSQL schema`() {
        assertEquals(40, entityManager.metamodel.entities.size)
        assertEquals(5, jdbc.queryForObject("select count(*) from flyway_schema_history where success", Int::class.java))
        tx {
            entityManager.clear()
            val order = entityManager.find(CustomerOrder::class.java, ids.getValue("CustomerOrder"))
            assertFalse(Hibernate.isInitialized(order.store))
            assertEquals("name", order.store.name)
        }
    }

    @Test
    fun `changing menu and sale prices preserves packed and ordered snapshots`() {
        tx {
            entityManager.find(MenuItem::class.java, ids.getValue("MenuItem")).listedPrice = BigDecimal("120000")
            entityManager.find(SaleBatch::class.java, ids.getValue("SaleBatch")).sellingPrice = BigDecimal("130000")
            entityManager.flush()
            entityManager.clear()
            assertEquals(0, BigDecimal("100000").compareTo(entityManager.find(BoxItem::class.java, ids.getValue("BoxItem")).unitPriceSnapshot))
            assertEquals(0, BigDecimal("100000").compareTo(entityManager.find(CustomerOrder::class.java, ids.getValue("CustomerOrder")).unitPrice))
        }
        assertThrows(Exception::class.java) {
            jdbc.update("update customer_order set unit_price=200000, total_amount=200000")
        }
    }

    @Test
    fun `active allocation is unique but released allocation history permits reuse`() {
        val insert = """insert into box_allocation (id, created_at, updated_at, version, store_id, order_id, box_id, sale_batch_at_allocation_id, status, reserved_at, expires_at)
            select ?, now(), now(), 0, store_id, order_id, box_id, sale_batch_at_allocation_id, 'HELD', reserved_at, expires_at from box_allocation limit 1"""
        assertThrows(DataIntegrityViolationException::class.java) { jdbc.update(insert, UUID.randomUUID()) }
        jdbc.update("update box_allocation set status='RELEASED', released_at=now(), release_reason='expired'")
        jdbc.update(insert, UUID.randomUUID())
        assertEquals(2, jdbc.queryForObject("select count(*) from box_allocation", Int::class.java))
    }

    @Test
    fun `cross store references and duplicate transactions are rejected`() {
        val otherStore = tx {
            Store(entityManager.find(Account::class.java, ids.getValue("Account")), "Other", "Other address", BigDecimal.ZERO, BigDecimal.ZERO, "Asia/Ho_Chi_Minh", com.foodrescue.backend.store.StoreStatus.CLOSED).also { entityManager.persist(it) }.id // T3.2: CLOSED so the one-open-store-per-owner index allows it
        }!!
        assertThrows(DataIntegrityViolationException::class.java) { jdbc.update("update box_item set store_id=?", otherStore) }
        assertThrows(DataIntegrityViolationException::class.java) { jdbc.update("update sale_batch set store_id=?", otherStore) }
        assertThrows(DataIntegrityViolationException::class.java) {
            jdbc.update("""insert into payment(id, created_at, updated_at, version, provider, provider_transaction_id, received_amount, currency, status, received_at)
                select ?, now(), now(), 0, provider, provider_transaction_id, received_amount, currency, status, received_at from payment""", UUID.randomUUID())
        }
    }

    @Test
    fun `one open pickup and one settlement of each ledger entry`() {
        assertThrows(DataIntegrityViolationException::class.java) {
            jdbc.update("""insert into pickup_session(id, created_at, updated_at, version, order_id, actor_id, status, started_at, idempotency_key)
                select ?, now(), now(), 0, order_id, actor_id, 'OPEN', now(), 'another' from pickup_session""", UUID.randomUUID())
        }
        assertThrows(DataIntegrityViolationException::class.java) {
            jdbc.update("""insert into settlement_line(id, created_at, store_id, settlement_id, ledger_entry_id, amount)
                select ?, now(), store_id, settlement_id, ledger_entry_id, amount from settlement_line""", UUID.randomUUID())
        }
    }

    @Test
    fun `audit and financial history cannot be rewritten or deleted`() {
        listOf("audit_log", "menu_price_history", "box_item", "box_inspection", "financial_policy", "financial_event", "ledger_entry").forEach { table ->
            assertThrows(Exception::class.java) { jdbc.update("update $table set created_at=now()") }
            assertThrows(Exception::class.java) { jdbc.update("delete from $table") }
        }
    }

    @Test
    fun `attachments require exactly one existing owner and no show deadlines require receipt evidence`() {
        assertThrows(DataIntegrityViolationException::class.java) { jdbc.update("update attachment set complaint_id=null") }
        assertThrows(DataIntegrityViolationException::class.java) { jdbc.update("update attachment set payment_id=?", ids.getValue("Payment")) }
        assertThrows(DataIntegrityViolationException::class.java) { jdbc.update("update no_show_case set dispute_deadline=now()") }
        assertThrows(DataIntegrityViolationException::class.java) { jdbc.update("delete from account") }
    }
    @Test
    fun `two transactions cannot reserve the same box concurrently`() {
        jdbc.update("update box_allocation set status='RELEASED', released_at=now()")
        val start = java.util.concurrent.CountDownLatch(1)
        val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val tasks = (1..2).map {
                executor.submit<Boolean> {
                    check(start.await(10, java.util.concurrent.TimeUnit.SECONDS))
                    try {
                        tx {
                            jdbc.update("""insert into box_allocation(id, created_at, updated_at, version, store_id, order_id, box_id, sale_batch_at_allocation_id, status, reserved_at, expires_at)
                                select ?, now(), now(), 0, store_id, order_id, box_id, sale_batch_at_allocation_id, 'HELD', reserved_at, expires_at from box_allocation limit 1""", UUID.randomUUID())
                        }
                        true
                    } catch (ex: DataIntegrityViolationException) { false }
                }
            }
            start.countDown()
            assertEquals(1, tasks.count { it.get(15, java.util.concurrent.TimeUnit.SECONDS) })
        } finally { executor.shutdownNow() }
    }

    @Test
    fun `draft settlement lines can be rebuilt but confirmed lines are frozen`() {
        jdbc.update("delete from settlement_line")
        jdbc.update("""insert into settlement_line(id, created_at, store_id, settlement_id, ledger_entry_id, amount)
            values (?, now(), ?, ?, ?, 100000)""", UUID.randomUUID(), ids.getValue("Store"), ids.getValue("Settlement"), ids.getValue("LedgerEntry"))
        jdbc.update("update settlement set status='CONFIRMED', confirmed_at=now(), confirmed_by_id=?", ids.getValue("Account"))
        assertThrows(DataIntegrityViolationException::class.java) { jdbc.update("delete from settlement_line") }
    }

}
