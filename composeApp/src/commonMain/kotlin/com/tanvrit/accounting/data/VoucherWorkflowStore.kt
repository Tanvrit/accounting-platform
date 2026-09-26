package com.tanvrit.accounting.data

import com.tanvrit.core.di.TanvritKoin
import com.tanvrit.core.network.AppJson
import com.tanvrit.storage.store.UserDefaults
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlin.time.Clock

/**
 * Client-local approval record for one voucher (feature #11). SDK 3.0.7's
 * `VoucherStatus` has no VERIFIED entry and `VoucherNetwork` no verify RPC, so
 * the verified stage and the approver note live here — keyed by voucher id,
 * per business, under `voucherWorkflow.<businessId>` — until a server field
 * exists. `Voucher.narration` was deliberately NOT used: it is user content,
 * and `dimensions`' server replace-semantics are unverified at this pin.
 */
@Serializable
data class VoucherApproval(
    val voucherId: String = "",
    val verified: Boolean = false,
    val verifiedBy: String = "",
    /** ISO instant the verification happened. */
    val verifiedAt: String = "",
    /** Free-text approver note shown on the verified voucher. */
    val note: String = "",
)

/**
 * Per-business approval overlay store. Same persistence shape as
 * [AccountingSettingsStore]: [UserDefaults] JSON (voucherId → record) with a
 * [StateFlow] screens collect; [selectBusiness] switches the active set.
 */
class VoucherWorkflowStore {
    private val defaults: UserDefaults = TanvritKoin.get()

    private var activeBusinessId: String = ""

    private val _approvals = MutableStateFlow<Map<String, VoucherApproval>>(emptyMap())
    val approvals: StateFlow<Map<String, VoucherApproval>> = _approvals.asStateFlow()

    fun selectBusiness(businessId: String) {
        if (businessId == activeBusinessId) return
        activeBusinessId = businessId
        _approvals.value = load(businessId)
    }

    fun approvalFor(voucherId: String): VoucherApproval? = _approvals.value[voucherId]

    fun isVerified(voucherId: String): Boolean = _approvals.value[voucherId]?.verified == true

    fun noteFor(voucherId: String): String = _approvals.value[voucherId]?.note.orEmpty()

    fun markVerified(
        voucherId: String,
        verifiedBy: String,
        note: String,
    ) {
        val next =
            _approvals.value +
                (
                    voucherId to
                        VoucherApproval(
                            voucherId = voucherId,
                            verified = true,
                            verifiedBy = verifiedBy,
                            verifiedAt = Clock.System.now().toString(),
                            note = note.trim(),
                        )
                )
        persist(next)
        _approvals.value = next
    }

    private fun key() = "$KEY_BASE.$activeBusinessId"

    private fun load(businessId: String): Map<String, VoucherApproval> {
        if (businessId.isBlank()) return emptyMap()
        val raw = defaults.retrieveScoped("$KEY_BASE.$businessId")
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            AppJson.json.decodeFromString<Map<String, VoucherApproval>>(raw)
        }.getOrDefault(emptyMap())
    }

    private fun persist(value: Map<String, VoucherApproval>) {
        if (activeBusinessId.isBlank()) return
        defaults.storeScoped(key(), AppJson.json.encodeToString(value))
    }

    private companion object {
        /** Prefix of the namespaced key — `"$KEY_BASE.<businessId>"`. */
        const val KEY_BASE = "voucherWorkflow"
    }
}
