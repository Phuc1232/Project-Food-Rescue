package com.foodrescue.app.feature.finance.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.finance.domain.*
import com.foodrescue.app.feature.store.presentation.component.*

@Composable
internal fun FinancialPolicyScreen(state: FinancialPolicyState, onEdit: () -> Unit, onRefresh: () -> Unit,
    modifier: Modifier = Modifier) {
    val scheduled = state.scheduled
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Text("Chính sách tài chính", style = MaterialTheme.typography.headlineMedium) }
        if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.error?.let { item { FinanceError(it, onRefresh, !state.busy) } }
        if (state.book != null) {
            item { StoreHeroCard("HOA HỒNG ĐANG ÁP DỤNG", revenueRate(state.current.commissionBps)) {
                Text(if (state.current.version == 0L) "Chính sách ban đầu" else "Phiên bản ${state.current.version}", color = Color.White)
                Text("Thanh toán 100% qua QR VNPAY", color = Color(0xFFD8E9DD))
            } }
            item { PolicyTermsCard(state.current) }
            item { FoodRescueCard {
                StoreSectionTitle("Phạm vi áp dụng")
                Text("Chính sách được chốt khi khách xác nhận giữ hộp. Các đơn đã tạo và kỳ đối soát đã xác nhận giữ nguyên điều kiện của mình.")
                Text("Hoa hồng tính trên tiền đơn sau khoản hoàn, làm tròn theo từng đơn.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
            if (scheduled != null) item { FoodRescueCard {
                StoreSectionTitle("Phiên bản sắp có hiệu lực")
                Text(revenueDate(scheduled.effectiveAt, "HH:mm · dd/MM/yyyy"), style = MaterialTheme.typography.titleMedium)
                Text("Hoa hồng ${revenueRate(scheduled.terms.commissionBps)} · Phiên bản ${scheduled.terms.version}")
                Text("Có thể tạo phiên bản tiếp theo sau thời điểm này.", style = MaterialTheme.typography.bodySmall)
            } }
            item { FoodRescueButton("Điều chỉnh chính sách", onEdit, Modifier.fillMaxWidth(),
                enabled = !state.busy && state.error == null && state.scheduled == null) }
            item { TextButton(onRefresh, enabled = !state.busy) { Text("Làm mới trạng thái hiệu lực") } }
            item { StoreSectionTitle("Lịch sử phiên bản") }
            if (state.book.versions.isEmpty()) item { Text("Chưa có lần điều chỉnh. Hoa hồng ban đầu là 20%.",
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
            state.book.versions.asReversed().forEach { policy -> item(key = policy.terms.version) { FoodRescueCard {
                Text("Phiên bản ${policy.terms.version}", style = MaterialTheme.typography.titleMedium)
                Text("Hiệu lực ${revenueDate(policy.effectiveAt, "HH:mm · dd/MM/yyyy")}")
                Text("Hoa hồng ${revenueRate(policy.terms.commissionBps)} · Hoàn khi hủy ${revenueRate(policy.terms.cancellationRefundBps)}")
                Text("Hạn hủy: trước giờ nhận ${policy.terms.cancellationMinutes} phút")
                Text("No-show: chờ ${policy.terms.noShowGraceMinutes} phút · hoàn ${revenueRate(policy.terms.noShowRefundBps)}")
                Text(policy.reason, style = MaterialTheme.typography.bodyMedium)
                Text("${policy.actor} · ${revenueDate(policy.publishedAt, "HH:mm · dd/MM/yyyy")}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } } }
        }
    }
}

@Composable
internal fun PolicyTermsCard(terms: FinancialTerms, modifier: Modifier = Modifier) {
    FoodRescueCard(modifier) {
        StoreSectionTitle("Hủy & hoàn tiền")
        StoreValue("Hạn hủy", if (terms.cancellationMinutes == 0) "Trước giờ nhận" else "Trước giờ nhận ${terms.cancellationMinutes} phút")
        StoreValue("Tỷ lệ hoàn khi hủy hợp lệ", revenueRate(terms.cancellationRefundBps))
        HorizontalDivider()
        StoreSectionTitle("Khách không đến nhận")
        StoreValue("Chờ thêm sau giờ nhận kết thúc", "${terms.noShowGraceMinutes} phút")
        StoreValue("Tỷ lệ hoàn theo chính sách", revenueRate(terms.noShowRefundBps))
        Text("Nhân viên xác minh No-show. Khoản hoàn theo chính sách được chuyển sang hàng chờ xử lý của quản trị viên.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
