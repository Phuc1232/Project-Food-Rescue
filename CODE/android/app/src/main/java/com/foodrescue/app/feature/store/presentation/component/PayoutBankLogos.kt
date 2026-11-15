package com.foodrescue.app.feature.store.presentation.component

import androidx.annotation.DrawableRes
import com.foodrescue.app.R

/** Original bank logos bundled from VietQR's public bank catalog for offline display. */
@DrawableRes
internal fun payoutBankLogo(code: String): Int? = when (code) {
    "ICB" -> R.drawable.bank_icb
    "VCB" -> R.drawable.bank_vcb
    "BIDV" -> R.drawable.bank_bidv
    "VBA" -> R.drawable.bank_vba
    "OCB" -> R.drawable.bank_ocb
    "MB" -> R.drawable.bank_mb
    "TCB" -> R.drawable.bank_tcb
    "ACB" -> R.drawable.bank_acb
    "VPB" -> R.drawable.bank_vpb
    "TPB" -> R.drawable.bank_tpb
    "STB" -> R.drawable.bank_stb
    "HDB" -> R.drawable.bank_hdb
    "VCCB" -> R.drawable.bank_vccb
    "SCB" -> R.drawable.bank_scb
    "VIB" -> R.drawable.bank_vib
    "SHB" -> R.drawable.bank_shb
    "EIB" -> R.drawable.bank_eib
    "MSB" -> R.drawable.bank_msb
    "SGICB" -> R.drawable.bank_sgicb
    "BAB" -> R.drawable.bank_bab
    "PVCB" -> R.drawable.bank_pvcb
    "MBV" -> R.drawable.bank_mbv
    "NCB" -> R.drawable.bank_ncb
    "SHBVN" -> R.drawable.bank_shbvn
    "ABB" -> R.drawable.bank_abb
    "VAB" -> R.drawable.bank_vab
    "NAB" -> R.drawable.bank_nab
    "PGB" -> R.drawable.bank_pgb
    "VIETBANK" -> R.drawable.bank_vietbank
    "BVB" -> R.drawable.bank_bvb
    "SEAB" -> R.drawable.bank_seab
    "COOPBANK" -> R.drawable.bank_coopbank
    "LPB" -> R.drawable.bank_lpb
    "KLB" -> R.drawable.bank_klb
    "HSBC" -> R.drawable.bank_hsbc
    "HLBVN" -> R.drawable.bank_hlbvn
    "IVB" -> R.drawable.bank_ivb
    "Vikki" -> R.drawable.bank_vikki
    "PBVN" -> R.drawable.bank_pbvn
    "SCVN" -> R.drawable.bank_scvn
    "CIMB" -> R.drawable.bank_cimb
    "UOB" -> R.drawable.bank_uob
    "CBB" -> R.drawable.bank_cbb
    "GPB" -> R.drawable.bank_gpb
    "VRB" -> R.drawable.bank_vrb
    "WVN" -> R.drawable.bank_wvn
    "KBank" -> R.drawable.bank_kbank
    else -> null
}
