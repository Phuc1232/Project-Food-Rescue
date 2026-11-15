package com.foodrescue.app.feature.store.domain

private fun String.validText(range: IntRange) = trim().length in range && none(Char::isISOControl)
private fun String.validTime() = Regex("^(?:[01][0-9]|2[0-3]):[0-5][0-9]$").matches(trim())

internal fun OwnerStoreProfile.validationErrors(): Set<StoreField> = buildSet {
    if (!name.validText(2..100)) add(StoreField.NAME)
    if (category !in storeCategories) add(StoreField.CATEGORY)
    if (!contactName.validText(2..100)) add(StoreField.CONTACT)
    if (!Regex("^(0[0-9]{9}|\\+84[0-9]{9})$").matches(phone.trim())) add(StoreField.PHONE)
    if (!address.validText(5..180)) add(StoreField.ADDRESS)
    if (!area.validText(2..100)) add(StoreField.AREA)
    if (description.trim().length > 500) add(StoreField.DESCRIPTION)
    if (!opensAt.validTime()) add(StoreField.OPEN)
    if (!closesAt.validTime() || closesAt.trim() == opensAt.trim()) add(StoreField.CLOSE)
    if (location == null || location.latitude !in -85.0..85.0) add(StoreField.LOCATION)
    if (contactEmail.isNotBlank() && (contactEmail.trim().length > 254 ||
        !Regex("^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?\\.[A-Za-z]{2,}$").matches(contactEmail.trim()))) add(StoreField.EMAIL)
}

internal fun StorePayoutAccount.validationErrors(banks: List<PayoutBank>): Set<PayoutField> = buildSet {
    if (banks.findBank(bank) == null) add(PayoutField.BANK)
    if (!holder.validText(2..100) || holder.none(Char::isLetter) ||
        holder.any { !it.isLetterOrDigit() && it != ' ' && it !in ".,&'-/()" }) add(PayoutField.HOLDER)
    if (!Regex("^[0-9]{6,19}$").matches(number.trim())) add(PayoutField.NUMBER)
}

internal fun OwnerStore.submit(profile: OwnerStoreProfile, nowMillis: Long): OwnerStore {
    check(status == StoreReviewStatus.DRAFT || status == StoreReviewStatus.CHANGES_REQUIRED)
    require(profile.validationErrors().isEmpty())
    return copy(profile = profile.normalized(), status = StoreReviewStatus.PENDING,
        submittedAtMillis = nowMillis, reviewNote = "")
}

internal fun OwnerStore.updateProfile(profile: OwnerStoreProfile): OwnerStore {
    check(status == StoreReviewStatus.APPROVED)
    require(profile.validationErrors().isEmpty())
    return copy(profile = profile.normalized())
}

internal fun OwnerStore.updatePayout(payout: StorePayoutAccount, banks: List<PayoutBank>): OwnerStore {
    check(status == StoreReviewStatus.APPROVED)
    require(payout.validationErrors(banks).isEmpty())
    return copy(payout = payout.normalized().copy(bank = checkNotNull(banks.findBank(payout.bank)).name))
}
