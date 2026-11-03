package vn.foodrescue.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import vn.foodrescue.app.data.remote.AccountProfile
import vn.foodrescue.app.data.session.SessionExpiredException
import vn.foodrescue.app.data.session.SessionRepository

data class AccountState(val profile: AccountProfile? = null, val loading: Boolean = true,
    val busy: Boolean = false, val error: String? = null, val epoch: Int = 0)

class AccountViewModel(private val repository: SessionRepository) : ViewModel() {
    private val mutable = MutableStateFlow(AccountState())
    val state = mutable.asStateFlow()
    init { reload() }
    fun reload() {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(loading = true, busy = true, error = null)
        viewModelScope.launch {
            try {
                val profile = repository.restore()
                mutable.value = mutable.value.copy(profile = profile, loading = false, busy = false,
                    epoch = mutable.value.epoch + if (profile == null) 1 else 0)
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = mutable.value.copy(busy = false, error = "Chưa kết nối được máy chủ. Kiểm tra mạng rồi thử lại.") }
        }
    }
    fun save(name: String, phone: String, done: () -> Unit) = work {
        val profile = repository.update(name, phone)
        mutable.value = mutable.value.copy(profile = profile)
        done()
    }
    fun logout() = work {
        repository.logout()
        mutable.value = AccountState(loading = false, epoch = mutable.value.epoch + 1)
    }
    fun dismissError() { mutable.value = mutable.value.copy(error = null) }
    private fun work(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: SessionExpiredException) { mutable.value = AccountState(loading = false, epoch = mutable.value.epoch + 1) }
            catch (error: HttpException) { mutable.value = mutable.value.copy(error = if (error.code() == 400)
                "Thông tin chưa hợp lệ. Họ tên từ 2–120 ký tự; số điện thoại gồm 10 số hoặc bắt đầu bằng +84."
                else "Chưa thực hiện được. Vui lòng thử lại.") }
            catch (_: Exception) { mutable.value = mutable.value.copy(error = "Chưa kết nối được máy chủ. Thay đổi chưa được xác nhận; hãy thử lại.") }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
}
