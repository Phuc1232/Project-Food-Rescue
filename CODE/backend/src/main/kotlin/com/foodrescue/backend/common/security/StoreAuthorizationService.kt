// AC-10,11: StoreAuthorizationService responsibility.
package com.foodrescue.backend.common.security

import com.foodrescue.backend.account.dto.StoreAccessResponse
import com.foodrescue.backend.account.AccountStatus
import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.common.exception.ApiException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
@Service
class StoreAuthorizationService(private val jdbc: JdbcTemplate,private val accounts: AccountRepository) {
    @Transactional(readOnly=true)
    fun contexts(accountId: UUID): List<StoreAccessResponse> {
        val a=accounts.findById(accountId).orElse(null)
        if(a==null || a.status!=AccountStatus.ACTIVE || a.emailVerifiedAt==null) return emptyList()
        return jdbc.query("""SELECT s.id,s.owner_id=? AS owner FROM store s WHERE s.owner_id=? OR EXISTS
            (SELECT 1 FROM staff_assignment a WHERE a.store_id=s.id AND a.account_id=? AND a.status='ACTIVE' AND a.revoked_at IS NULL)
            ORDER BY s.id""",{ rs,_ ->
            val id=rs.getObject("id",UUID::class.java)
            val permissions=jdbc.queryForList("""SELECT p.code FROM permission p JOIN staff_permission sp ON sp.permission_id=p.id
                JOIN staff_assignment a ON a.id=sp.assignment_id
                WHERE a.store_id=? AND a.account_id=? AND a.status='ACTIVE' AND a.revoked_at IS NULL ORDER BY p.code""",String::class.java,id,accountId)
            StoreAccessResponse(id,rs.getBoolean("owner"),permissions.filterNotNull())
        },accountId,accountId,accountId)
    }
    @Transactional(readOnly=true)
    fun requirePermission(accountId: UUID,storeId: UUID,permission: String) {
        val context=contexts(accountId).find { it.storeId==storeId }
        if(context==null || (!context.owner && permission !in context.permissions.filterNotNull())) throw ApiException(403,"ACCESS_DENIED","Access denied")
    }
}
