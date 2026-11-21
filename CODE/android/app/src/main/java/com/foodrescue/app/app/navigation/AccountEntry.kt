package com.foodrescue.app.app.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.FoodRescueButton
import com.foodrescue.app.feature.staff.domain.StaffAccessRepository
import kotlinx.coroutines.CancellationException
import com.foodrescue.app.feature.auth.domain.AdminIdentity
import com.foodrescue.app.feature.account.domain.*
import androidx.activity.compose.BackHandler

/** Resolves the frontend identity before any role surface is opened. */
internal suspend fun accountRole(email: String, isOwner: Boolean, employees: StaffAccessRepository): RoleNavigation.Role =
    if (AdminIdentity.matches(email)) RoleNavigation.Role.ADMIN else if (isOwner) RoleNavigation.Role.OWNER else if (employees.find(email) != null) RoleNavigation.Role.STAFF else RoleNavigation.Role.CUSTOMER

@Composable
internal fun AccountEntry(name: String, email: String, isOwner: Boolean, employees: StaffAccessRepository,
    accounts: AccountSessionRepository, onResolved: (AccountLogin, RoleNavigation.Role) -> Unit,
    onCancel: () -> Unit, modifier: Modifier = Modifier) {
    var attempt by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var suspended by remember { mutableStateOf(false) }
    val resolved by rememberUpdatedState(onResolved)
    BackHandler(onBack = onCancel)
    LaunchedEffect(name, email, isOwner, employees, accounts, attempt) {
        error = null; suspended = false
        try {
            val role = accountRole(email, isOwner, employees)
            resolved(accounts.signIn(name, email), role)
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (locked: AccountSuspended) { suspended = true; error = "Tài khoản đang tạm khóa. ${locked.reason}" }
        catch (_: Exception) { error = "Chưa kiểm tra được tài khoản. Vui lòng thử lại." }
    }
    Column(modifier.fillMaxSize().systemBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally) {
        if (error != null) {
            Text(checkNotNull(error))
            if (!suspended) FoodRescueButton("Thử lại", { attempt++ })
        } else { CircularProgressIndicator(); Text("Đang kiểm tra tài khoản…") }
        TextButton(onCancel) { Text("Quay lại đăng nhập") }
    }
}
