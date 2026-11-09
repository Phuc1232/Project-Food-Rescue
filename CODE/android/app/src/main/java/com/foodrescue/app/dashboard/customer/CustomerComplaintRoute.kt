package com.foodrescue.app.dashboard.customer

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.foodrescue.app.feature.complaint.domain.ComplaintOrder
import com.foodrescue.app.feature.complaint.presentation.*
import com.foodrescue.app.feature.order.presentation.BookingUiState

@Composable
internal fun CustomerComplaintRoute(destination: String, vm: ComplaintViewModel, booking: BookingUiState,
    onBack: () -> Unit, onOpen: (String) -> Unit, onSubmitted: (String) -> Unit,
    onOrders: () -> Unit, modifier: Modifier = Modifier) {
    val orders = remember(booking.reservations, booking.catalog) {
        booking.reservations.filter { it.payment != null }.sortedByDescending { it.createdAtMillis }.map { order ->
            val box = booking.catalog.firstOrNull { it.id == order.boxId }
            ComplaintOrder(order.id, order.reference, box?.store ?: "Cửa hàng", box?.name ?: "Hộp bất ngờ",
                order.quantity, order.total, order.createdAtMillis)
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { selected ->
        vm.addEvidence(selected.map { it.toString() })
    }
    when {
        destination == "complaints" -> CustomerComplaintsScreen(vm.state, onBack, { onOpen("complaint-new") },
            { onOpen("complaint:$it") }, vm::selectFilter, modifier)
        destination.startsWith("complaint:") -> ComplaintDetailScreen(vm.state.cases.firstOrNull { it.id == destination.substringAfter(':') },
            onBack, { onOpen("reservation:$it") }, modifier)
        else -> {
            LaunchedEffect(destination) { vm.prepare(destination.substringAfter(':', "").takeIf(String::isNotBlank)) }
            ComplaintFormScreen(vm.state, orders, onBack, onOrders, vm::requestOrderChange, vm::chooseReason,
                vm::changeDescription, onAddEvidence = {
                    try { picker.launch(arrayOf("image/*")) }
                    catch (_: ActivityNotFoundException) { vm.imagePickerUnavailable() }
                    catch (_: SecurityException) { vm.imagePickerUnavailable() }
                }, vm::removeEvidence, onSubmit = { vm.submit(orders)?.let(onSubmitted) },
                onCase = { onOpen("complaint:$it") }, vm::confirmOrderChange, vm::dismissOrderChange, modifier)
        }
    }
}
