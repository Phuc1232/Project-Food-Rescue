package com.foodrescue.app.feature.account.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.foodrescue.app.core.designsystem.component.*

@Composable
internal fun AccountAccessScreen(state: AccountAccessState, onRetry: () -> Unit, onExit: () -> Unit,
    modifier: Modifier = Modifier) {
    BackHandler { onExit() }
    FoodRescuePage("Truy cập tài khoản", onExit, modifier, footer = {
        FoodRescueButton("Về đăng nhập", onExit, Modifier.fillMaxWidth())
    }) {
        if (state.loading) {
            CircularProgressIndicator()
            Text("Đang kiểm tra tài khoản…", style = MaterialTheme.typography.titleMedium)
        } else FoodRescueCard {
            FoodRescueGlyph(FoodRescueIcon.PERSON)
            Text(when {
                state.error -> "Chưa kiểm tra được tài khoản"
                state.expired -> "Vui lòng đăng nhập lại"
                else -> "Tài khoản đang tạm khóa"
            }, style = MaterialTheme.typography.headlineSmall)
            Text(when {
                state.error -> "Vui lòng thử lại để tiếp tục truy cập."
                state.expired -> "Trạng thái truy cập đã thay đổi. Đăng nhập lại để bắt đầu phiên mới."
                else -> "Tài khoản hiện chưa thể sử dụng FoodRescue."
            })
            state.reason?.let { AccountValue("Lý do", it) }
            if (state.error) OutlinedButton(onRetry) { Text("Thử lại") }
        }
    }
}

