// AC-08,09,21,22: SessionService responsibility.
package com.foodrescue.backend.auth.service

import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.auth.repository.LoginSessionRepository
import com.foodrescue.backend.auth.dto.SessionResponse
import com.foodrescue.backend.audit.AuthAuditService
import com.foodrescue.backend.common.security.AccountPrincipal
import com.foodrescue.backend.common.response.PageResponse
import com.foodrescue.backend.common.validation.PageQuery
import com.foodrescue.backend.common.exception.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID
@Service
class SessionService(private val accounts: AccountRepository,private val sessions: LoginSessionRepository,private val audit: AuthAuditService,private val clock: Clock) {
    fun revokeAll(account: Account,action: String) {
        sessions.findAllByAccountId(account.id!!).filter { it.revokedAt==null }.forEach { it.revokedAt=clock.instant() }
        audit.record(account,action)
    }
    @Transactional fun logoutAll(p: AccountPrincipal) { revokeAll(accounts.findForUpdate(p.accountId) ?: authenticationFailed(),"LOGOUT_ALL") }
    @Transactional fun revoke(p: AccountPrincipal,id: UUID) {
        val a=accounts.findForUpdate(p.accountId) ?: authenticationFailed()
        val s=sessions.findById(id).orElse(null)
        if(s==null || s.account.id!=p.accountId) throw ApiException(404,"NOT_FOUND","Session not found")
        if(s.revokedAt==null) { s.revokedAt=clock.instant(); audit.record(a,"SESSION_REVOKED",id) }
    }
    @Transactional(readOnly=true)
    fun list(p: AccountPrincipal,page: Int,size: Int,sort: String): PageResponse<SessionResponse> {
        val result=sessions.findAllByAccountId(p.accountId,PageQuery().pageable(page,size,sort))
        return PageResponse(result.content.map { SessionResponse(it.id!!,it.createdAt,it.expiresAt,it.revokedAt,it.id==p.sessionId) },page,size,result.totalElements,result.totalPages)
    }
}
