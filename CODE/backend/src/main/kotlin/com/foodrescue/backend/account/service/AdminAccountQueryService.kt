// AC-01,02 (T3.5): AdminAccountQueryService responsibility.
package com.foodrescue.backend.account.service

import com.foodrescue.backend.account.AccountStatus
import com.foodrescue.backend.account.PlatformRole
import com.foodrescue.backend.account.dto.*
import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.account.repository.AccountRoleRepository
import com.foodrescue.backend.audit.repository.AuditLogRepository
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.response.PageResponse
import jakarta.persistence.EntityManager
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/** UC-68/UC-69. Callers must be ADMIN (checked at the controller). */
@Service
class AdminAccountQueryService(
    private val accounts: AccountRepository,
    private val roles: AccountRoleRepository,
    private val audits: AuditLogRepository,
    private val outstanding: OutstandingWorkCounter,
    private val entityManager: EntityManager,
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper
) {
    /** Dynamic JPQL keeps absent filters out of the query (T3.5 P1). */
    @Transactional(readOnly = true)
    fun search(query: String?, role: PlatformRole?, status: AccountStatus?, page: Int, size: Int): PageResponse<AdminAccountSummary> {
        if (page < 0 || size !in 1..100) throw ApiException(400, "INVALID_PAGINATION", "Invalid page, size or sort")
        val where = mutableListOf<String>(); val params = mutableMapOf<String, Any>()
        query?.trim()?.takeIf { it.isNotEmpty() }?.let { where += "lower(a.loginIdentifier) like :q"; params["q"] = "%" + it.lowercase().replace("%", "") + "%" }
        role?.let { where += "exists (select 1 from AccountRole r where r.account = a and r.role = :role)"; params["role"] = it }
        status?.let { where += "a.status = :status"; params["status"] = it }
        val clause = if (where.isEmpty()) "" else " where " + where.joinToString(" and ")
        val total = entityManager.createQuery("select count(a) from Account a$clause", java.lang.Long::class.java)
            .also { q -> params.forEach { (k, v) -> q.setParameter(k, v) } }.singleResult.toLong()
        val content = entityManager.createQuery("select a from Account a$clause order by a.createdAt desc, a.id", Account::class.java)
            .also { q -> params.forEach { (k, v) -> q.setParameter(k, v) } }.setFirstResult(page * size).setMaxResults(size).resultList
        return PageResponse(content.map(::summary), page, size, total, ((total + size - 1) / size).toInt())
    }

    @Transactional(readOnly = true)
    fun detail(id: UUID): AdminAccountDetail = detail(accounts.findById(id).orElse(null) ?: throw ApiException(404, "NOT_FOUND", "Resource not found"))

    /** Call inside a transaction. */
    fun detail(a: Account): AdminAccountDetail {
        val history = audits.findTop50ByEntityIdAndEntityTypeInOrderByCreatedAtDesc(a.id!!, listOf("AUTH", ENTITY)).map {
            AccountHistoryItem(it.action, it.occurredAt, it.actor?.id, it.beforeState?.let(mapper::readTree), it.afterState?.let(mapper::readTree))
        }
        return AdminAccountDetail(summary(a), a.statusReason, a.statusChangedAt, stores(a.id!!), outstanding.customerOrders(a.id!!),
            outstanding.storeOrders(a.id!!), history)
    }

    fun summary(a: Account) = AdminAccountSummary(a.id!!, a.loginIdentifier, a.displayName,
        roles.findAllByAccountId(a.id!!).map { it.role.name }.sorted(), a.status.name, a.emailVerifiedAt != null, a.createdAt)

    private fun stores(accountId: UUID): List<AccountStoreLink> {
        val owned = jdbc.query("SELECT id, name, status FROM store WHERE owner_id = ? ORDER BY created_at", { rs, _ ->
            AccountStoreLink(rs.getObject("id", UUID::class.java), rs.getString("name"), rs.getString("status"), "OWNER", null, emptyList()) }, accountId)
        val staff = jdbc.query("""SELECT s.id, s.name, s.status, a.status AS assignment, a.id AS assignment_id FROM staff_assignment a
            JOIN store s ON s.id = a.store_id WHERE a.account_id = ? ORDER BY a.assigned_at""", { rs, _ ->
            val permissions = jdbc.queryForList("""SELECT p.code FROM staff_permission sp JOIN permission p ON p.id = sp.permission_id
                WHERE sp.assignment_id = ? ORDER BY p.code""", String::class.java, rs.getObject("assignment_id", UUID::class.java)).filterNotNull()
            AccountStoreLink(rs.getObject("id", UUID::class.java), rs.getString("name"), rs.getString("status"), "STAFF", rs.getString("assignment"), permissions)
        }, accountId)
        return owned + staff
    }

    companion object { const val ENTITY = "ACCOUNT" }
}
