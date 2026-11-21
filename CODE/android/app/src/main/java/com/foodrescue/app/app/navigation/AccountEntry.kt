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

/** Resolves the frontend identity before any role surface is opened. */
internal suspend fun accountRole(email: String, isOwner: Boolean, employees: StaffAccessRepository): RoleNavigation.Role =
    if (com.foodrescue.app.feature.auth.domain.AdminIdentity.matches(email)) RoleNavigation.Role.ADMIN else if (isOwner) RoleNavigation.Role.OWNER else if (employees.find(email) != null) RoleNavigation.Role.STAFF else RoleNavigation.Role.CUSTOMER

@Composable
internal fun AccountEntry(email: String, isOwner: Boolean, employees: StaffAccessRepository,
    onResolved: (RoleNavigation.Role) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    var attempt by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf(false) }
    val resolved by rememberUpdatedState(onResolved)
    LaunchedEffect(email, isOwner, employees, attempt) {
        error = false
        try { resolved(accountRole(email, isOwner, employees)) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true }
    }
    Column(modifier.fillMaxSize().systemBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally) {
        if (error) {
            Text("Chưa kiểm tra được tài khoản. Vui lòng thử lại.")
            FoodRescueButton("Thử lại", { attempt++ })
        } else { CircularProgressIndicator(); Text("Đang kiểm tra tài khoản…") }
        TextButton(onCancel) { Text("Quay lại đăng nhập") }
    }
}
