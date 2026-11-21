package com.foodrescue.app.feature.store.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foodrescue.app.feature.store.domain.*
import com.foodrescue.app.core.common.GeoPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal class StoreManagementViewModel(
    private val savedState: SavedStateHandle,
    private val repository: OwnerStoreRepository,
    banks: List<PayoutBank> = emptyList(),
) : ViewModel() {
    var state by mutableStateOf(StoreManagementState(banks = banks))
        private set

    fun load(force: Boolean = false) {
        if ((!force && !state.loading) || state.busy) return
        state = state.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val store = repository.load()
                val form = savedState.get<ArrayList<String>>("store.form")?.takeIf { it.size in listOf(9, 11, 12) }
                val draft = form?.let {
                    val point = if (it.size == 9) store.profile.location else runCatching {
                        GeoPoint(it[9].toDouble(), it[10].toDouble())
                    }.getOrNull()
                    OwnerStoreProfile(it[0], it[1], it[2], it[3], it[4], it[5], it[6], it[7], it[8], point, it.getOrNull(11) ?: store.profile.contactEmail)
                }
                    ?: store.profile
                val bank = savedState.get<ArrayList<String>>("store.bank")?.takeIf { it.size == 3 }
                state = state.copy(store = store, draft = draft,
                    payoutDraft = bank?.let { StorePayoutAccount(it[0], it[1], it[2]) } ?: store.payout ?: StorePayoutAccount(),
                    step = (savedState.get<Int>("store.step") ?: 0).coerceIn(0, 2),
                    accepted = savedState["store.accepted"] ?: false, loading = false, busy = false)
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { state = state.copy(busy = false, error = "Không tải được thông tin cửa hàng. Vui lòng thử lại.") }
        }
    }

    fun beginProfileEdit() {
        if (state.busy) return
        state = state.copy(draft = state.store?.profile ?: OwnerStoreProfile(), errors = emptySet(), error = null)
        rememberDraft()
    }

    fun beginPayoutEdit() {
        if (state.busy) return
        state = state.copy(payoutDraft = state.store?.payout ?: StorePayoutAccount(), payoutErrors = emptySet(), error = null)
        rememberPayout()
    }

    fun edit(field: StoreField, input: String) {
        if (state.busy) return
        val value = input.take(when (field) { StoreField.DESCRIPTION -> 500; StoreField.EMAIL -> 254; else -> 180 })
        val draft = state.draft
        state = state.copy(draft = when (field) {
            StoreField.NAME -> draft.copy(name = value)
            StoreField.CATEGORY -> draft.copy(category = value)
            StoreField.CONTACT -> draft.copy(contactName = value)
            StoreField.PHONE -> draft.copy(phone = value.take(13))
            StoreField.ADDRESS -> draft.copy(address = value, location = draft.location.takeIf { value.trim() == draft.address.trim() })
            StoreField.AREA -> draft.copy(area = value, location = draft.location.takeIf { value.trim() == draft.area.trim() })
            StoreField.DESCRIPTION -> draft.copy(description = value)
            StoreField.OPEN -> draft.copy(opensAt = value.take(5))
            StoreField.CLOSE -> draft.copy(closesAt = value.take(5))
            StoreField.LOCATION -> draft
            StoreField.EMAIL -> draft.copy(contactEmail = value)
        }, errors = state.errors - field, error = null)
        rememberDraft()
    }

    fun selectLocation(point: GeoPoint) {
        if (state.busy || point.latitude !in -85.0..85.0) return
        state = state.copy(draft = state.draft.copy(location = point),
            errors = state.errors - StoreField.LOCATION, error = null)
        rememberDraft()
    }

    fun editPayout(field: PayoutField, input: String) {
        if (state.busy) return
        val value = input.take(100)
        state = state.copy(payoutDraft = when (field) {
            PayoutField.BANK -> state.payoutDraft.copy(bank = value)
            PayoutField.HOLDER -> state.payoutDraft.copy(holder = value)
            PayoutField.NUMBER -> state.payoutDraft.copy(number = value.filter { it in '0'..'9' }.take(19))
        }, payoutErrors = state.payoutErrors - field, error = null)
        rememberPayout()
    }

    fun accept(value: Boolean) {
        state = state.copy(accepted = value, acceptanceError = false)
        savedState["store.accepted"] = value
    }

    fun previousStep() { if (!state.busy) setStep((state.step - 1).coerceAtLeast(0)) }
    fun nextStep() {
        if (state.busy) return
        val relevant = if (state.step == 0) setOf(StoreField.NAME, StoreField.CATEGORY, StoreField.DESCRIPTION)
            else StoreField.entries.toSet() - setOf(StoreField.NAME, StoreField.CATEGORY, StoreField.DESCRIPTION)
        val errors = state.draft.validationErrors().intersect(relevant)
        state = state.copy(errors = errors)
        if (errors.isEmpty()) setStep((state.step + 1).coerceAtMost(2))
    }
    private fun setStep(step: Int) { state = state.copy(step = step); savedState["store.step"] = step }

    fun saveDraft() = perform(StoreSaved.DRAFT) { repository.saveDraft(state.draft) }
    fun submit() {
        val errors = state.draft.validationErrors()
        state = state.copy(errors = errors, acceptanceError = !state.accepted)
        if (errors.isNotEmpty()) { setStep(if (StoreField.NAME in errors || StoreField.CATEGORY in errors) 0 else 1); return }
        if (!state.accepted) return
        perform(StoreSaved.APPLICATION) { repository.submit(state.draft) }
    }
    fun saveProfile() {
        val errors = state.draft.validationErrors()
        state = state.copy(errors = errors)
        if (errors.isEmpty()) perform(StoreSaved.PROFILE) { repository.updateProfile(state.draft) }
    }
    fun validatePayout(): Boolean {
        val errors = state.payoutDraft.validationErrors(state.banks)
        state = state.copy(payoutErrors = errors)
        return errors.isEmpty()
    }
    fun savePayout() {
        if (validatePayout()) perform(StoreSaved.PAYOUT) { repository.updatePayout(state.payoutDraft) }
    }
    fun consumeSaved() { state = state.copy(saved = null) }
    fun clearTransient() {
        state = state.copy(draft = state.store?.profile ?: OwnerStoreProfile(),
            payoutDraft = state.store?.payout ?: StorePayoutAccount(), step = 0, accepted = false, acceptanceError = false,
            errors = emptySet(), payoutErrors = emptySet(), error = null, saved = null)
        savedState.remove<ArrayList<String>>("store.form")
        savedState.remove<ArrayList<String>>("store.bank")
        savedState.remove<Int>("store.step")
        savedState.remove<Boolean>("store.accepted")
    }
    private fun perform(kind: StoreSaved, action: suspend () -> OwnerStore) {
        if (state.busy || state.store == null) return
        state = state.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val store = action()
                state = state.copy(store = store, busy = false, saved = kind)
                if (kind != StoreSaved.DRAFT) {
                    savedState.remove<ArrayList<String>>("store.form")
                    savedState.remove<ArrayList<String>>("store.bank")
                    savedState.remove<Int>("store.step")
                    savedState.remove<Boolean>("store.accepted")
                }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { state = state.copy(busy = false, error = "Chưa lưu được thông tin. Nội dung của bạn vẫn được giữ lại, vui lòng thử lại.") }
        }
    }
    private fun rememberDraft() {
        val d = state.draft
        savedState["store.form"] = arrayListOf(d.name, d.category, d.contactName, d.phone, d.address, d.area, d.description,
            d.opensAt, d.closesAt, d.location?.latitude?.toString().orEmpty(), d.location?.longitude?.toString().orEmpty(), d.contactEmail)
    }
    private fun rememberPayout() {
        val d = state.payoutDraft
        savedState["store.bank"] = arrayListOf(d.bank, d.holder, d.number)
    }
}
