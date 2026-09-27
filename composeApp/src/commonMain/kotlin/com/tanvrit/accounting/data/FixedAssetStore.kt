package com.tanvrit.accounting.data

import com.tanvrit.core.di.TanvritKoin
import com.tanvrit.core.extension.uniqueId
import com.tanvrit.core.network.AppJson
import com.tanvrit.storage.store.UserDefaults
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/**
 * A fixed-asset register entry (roadmap #6). Client-local: no fixed-assets
 * domain exists in SDK 3.0.7, so the register lives in [UserDefaults] under
 * `fixedAssets.<businessId>`; depreciation itself lands in the REAL ledger as
 * DRAFT journal vouchers (see `screens/fixedAssets/FixedAssetEngine`).
 *
 * [cost]/[salvage] are major-unit strings as typed ("125000.00").
 * [postedRows] lists charged schedule row indexes (idempotency anchor paired
 * with voucher referenceId `depr:<assetId>:<rowIndex>`).
 */
@Serializable
data class FixedAsset(
    val id: String = "",
    val businessId: String = "",
    val name: String = "",
    val assetAccountId: String = "",
    val assetAccountCode: String = "",
    val assetAccountName: String = "",
    val depExpenseAccountId: String = "",
    val depExpenseAccountCode: String = "",
    val depExpenseAccountName: String = "",
    val accumDepAccountId: String = "",
    val accumDepAccountCode: String = "",
    val accumDepAccountName: String = "",
    val cost: String = "",
    val salvage: String = "",
    /** `DepreciationMethod.code` ("SLM"/"WDV") — a string so records survive enum growth. */
    val method: String = "WDV",
    val ratePercent: String = "",
    val startDate: String = "",
    val postedRows: List<Int> = emptyList(),
)

/** Per-business fixed-asset store — same persistence shape as [VoucherTemplateStore]. */
class FixedAssetStore {
    private val defaults: UserDefaults = TanvritKoin.get()

    private var activeBusinessId: String = ""

    private val _assets = MutableStateFlow<List<FixedAsset>>(emptyList())
    val assets: StateFlow<List<FixedAsset>> = _assets.asStateFlow()

    fun selectBusiness(businessId: String) {
        if (businessId == activeBusinessId) return
        activeBusinessId = businessId
        _assets.value = load(businessId)
    }

    fun save(asset: FixedAsset): FixedAsset {
        val saved =
            asset.copy(
                id = asset.id.ifBlank { uniqueId() },
                businessId = activeBusinessId,
            )
        val next = _assets.value.filterNot { it.id == saved.id } + saved
        persist(next)
        _assets.value = next
        return saved
    }

    fun update(asset: FixedAsset) {
        val next = _assets.value.map { if (it.id == asset.id) asset else it }
        persist(next)
        _assets.value = next
    }

    fun delete(assetId: String) {
        val next = _assets.value.filterNot { it.id == assetId }
        persist(next)
        _assets.value = next
    }

    private fun key() = "$KEY_BASE.$activeBusinessId"

    private fun load(businessId: String): List<FixedAsset> {
        if (businessId.isBlank()) return emptyList()
        val raw = defaults.retrieveScoped("$KEY_BASE.$businessId")
        if (raw.isBlank()) return emptyList()
        return runCatching {
            AppJson.json.decodeFromString<List<FixedAsset>>(raw)
        }.getOrDefault(emptyList())
            .filter { it.businessId == businessId || it.businessId.isBlank() }
            .sortedBy { it.name.lowercase() }
    }

    private fun persist(value: List<FixedAsset>) {
        if (activeBusinessId.isBlank()) return
        defaults.storeScoped(key(), AppJson.json.encodeToString(value))
    }

    private companion object {
        const val KEY_BASE = "fixedAssets"
    }
}
