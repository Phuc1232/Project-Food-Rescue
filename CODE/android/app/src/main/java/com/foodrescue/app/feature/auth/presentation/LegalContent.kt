package com.foodrescue.app.feature.auth.presentation

import androidx.annotation.StringRes
import com.foodrescue.app.R

enum class LegalDocument { TERMS, PRIVACY }

internal data class LegalSection(
    @param:StringRes @get:StringRes val title: Int,
    @param:StringRes @get:StringRes val body: Int,
)

internal data class LegalContent(
    @param:StringRes @get:StringRes val title: Int,
    @param:StringRes @get:StringRes val summary: Int,
    val sections: List<LegalSection>,
)

// These documents describe this local UI preview. Review the actual data flows and
// operator details before connecting services or publishing a production policy.
internal fun legalContent(document: LegalDocument): LegalContent = when (document) {
    LegalDocument.TERMS -> LegalContent(
        R.string.legal_terms_title,
        R.string.legal_terms_summary,
        listOf(
            LegalSection(R.string.terms_scope_title, R.string.terms_scope_body),
            LegalSection(R.string.terms_box_title, R.string.terms_box_body),
            LegalSection(R.string.terms_account_title, R.string.terms_account_body),
            LegalSection(R.string.terms_payment_title, R.string.terms_payment_body),
            LegalSection(R.string.terms_pickup_title, R.string.terms_pickup_body),
            LegalSection(R.string.terms_support_title, R.string.terms_support_body),
            LegalSection(R.string.terms_usage_title, R.string.terms_usage_body),
            LegalSection(R.string.terms_changes_title, R.string.terms_changes_body),
        ),
    )
    LegalDocument.PRIVACY -> LegalContent(
        R.string.legal_privacy_title,
        R.string.legal_privacy_summary,
        listOf(
            LegalSection(R.string.privacy_data_title, R.string.privacy_data_body),
            LegalSection(R.string.privacy_purpose_title, R.string.privacy_purpose_body),
            LegalSection(R.string.privacy_storage_title, R.string.privacy_storage_body),
            LegalSection(R.string.privacy_permissions_title, R.string.privacy_permissions_body),
            LegalSection(R.string.privacy_sharing_title, R.string.privacy_sharing_body),
            LegalSection(R.string.privacy_choices_title, R.string.privacy_choices_body),
            LegalSection(R.string.privacy_contact_title, R.string.privacy_contact_body),
            LegalSection(R.string.privacy_changes_title, R.string.privacy_changes_body),
        ),
    )
}
