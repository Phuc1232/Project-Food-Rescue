package vn.foodrescue.app.ui.customer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal interface CustomerStorage {
    fun load(): CustomerSnapshot
    fun save(snapshot: CustomerSnapshot)
}
internal enum class PreviewPaymentResult(val label: String) {
    SUCCESS("Thành công"), FAILURE("Thất bại"), PENDING("Chưa có kết quả"), LATE("Tiền đến muộn")
}
internal class CustomerViewModel(application: Application) : AndroidViewModel(application) {
    private val storage = createCustomerStorage(application)
    private val engine = CustomerOrderEngine(storage.load())
    private val mutable = MutableStateFlow(engine.snapshot)
    val state = mutable.asStateFlow()
    private val mutableNow = MutableStateFlow(System.currentTimeMillis())
    val now = mutableNow.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    private val payments = mutableListOf<Job>()
    init { viewModelScope.launch { while (true) { refresh(); delay(1000) } } }
    fun dismissError() { mutableError.value = null }
    private fun publish() {
        if (engine.snapshot != mutable.value) { storage.save(engine.snapshot); mutable.value = engine.snapshot }
    }
    private fun act(action: () -> Unit): Boolean = runCatching { action(); publish(); true }.getOrElse {
        mutableError.value = it.message ?: "Chưa thực hiện được. Vui lòng thử lại."
        false
    }
    private fun refresh() {
        mutableNow.value = System.currentTimeMillis()
        engine.refresh(mutableNow.value)
        publish()
    }
    private fun catalogBox(box: DemoBox) = demoBoxes.first { it.id == box.id }.copy(distanceMeters = box.distanceMeters)
    fun available(box: DemoBox, pickup: Long) = engine.available(catalogBox(box), pickup)
    fun place(box: DemoBox, count: Int, plan: PaymentPlan, method: PaymentMethod, pickup: Pair<Long, Long>): String? {
        var result: String? = null
        act { result = engine.place(catalogBox(box), count, plan, method, pickup, System.currentTimeMillis()).id }
        return result
    }
    fun pay(id: String, result: PreviewPaymentResult) {
        if (!act { engine.beginPayment(id, System.currentTimeMillis()) }) return
        payments += viewModelScope.launch {
            delay(1600)
            if (result != PreviewPaymentResult.PENDING) act {
                if (result == PreviewPaymentResult.LATE) engine.expirePreview(id)
                engine.paymentResult(id, result != PreviewPaymentResult.FAILURE, System.currentTimeMillis())
            }
        }
    }
    fun checkPending(id: String) { act { engine.paymentResult(id, true, System.currentTimeMillis()) } }
    fun stopPayment(id: String) = act { engine.stopPayment(id, System.currentTimeMillis()) }
    fun cancel(id: String, reason: String) = act { engine.requestCancellation(id, reason, System.currentTimeMillis()) }
    fun complain(id: String, reason: ComplaintReason, content: String, evidence: List<Evidence>) =
        act { engine.complain(id, reason, content, evidence, System.currentTimeMillis()) }
    fun saveProfile(name: String, phone: String) = act { engine.updateProfile(name, phone) }
    fun addExamples() { act { engine.addExamples(System.currentTimeMillis()) } }
    fun finishRefund(id: String) { act { engine.finishRefund(id) } }
    fun resolveComplaint(id: String, accepted: Boolean) { act { engine.resolveComplaint(id, accepted) } }
    fun resolveCancellation(id: String, accepted: Boolean) { act { engine.resolveCancellation(id, accepted) } }
    fun clearPreview() { payments.forEach { it.cancel() }; payments.clear(); act { engine.clear() }; dismissError() }
}
