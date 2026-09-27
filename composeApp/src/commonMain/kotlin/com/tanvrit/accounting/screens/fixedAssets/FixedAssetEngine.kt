package com.tanvrit.accounting.screens.fixedAssets

import com.tanvrit.accounting.data.FixedAsset
import com.tanvrit.accounting.data.FixedAssetStore
import com.tanvrit.accounting.network.VoucherNetwork
import com.tanvrit.accounting.repository.FiscalPeriodRepository
import com.tanvrit.accounting.repository.VoucherRepository
import com.tanvrit.core.di.TanvritKoin
import com.tanvrit.core.feature.accounting.model.FiscalPeriodStatus
import com.tanvrit.core.feature.accounting.model.VoucherLineItem
import com.tanvrit.core.feature.accounting.model.VoucherType
import com.tanvrit.core.feature.accounting.network.CreateVoucherRequest
import com.tanvrit.core.feature.money.Money
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/** Outcome of a charge attempt for one asset. */
data class DepreciationChargeOutcome(
    val assetId: String,
    val chargedRowIndex: Int?,
    val voucherId: String?,
    val error: String?,
)

/**
 * Charges depreciation as DRAFT journal vouchers (roadmap #6) — the user's
 * Approvals queue gets review + posting power, exactly like recurring
 * vouchers. Semantics:
 *
 *  - One voucher per asset per row index: referenceId `depr:<assetId>:<row>`
 *    is the idempotency anchor — before drafting, the local voucher cache is
 *    checked so a re-run NEVER double-charges (server-side dedupe cannot be
 *    verified at SDK 3.0.7; the anchor keeps the local path honest).
 *  - Stops with an honest error when no OPEN fiscal period contains today.
 *  - Never auto-posts.
 */
class FixedAssetEngine(
    private val store: FixedAssetStore = TanvritKoin.get(),
    private val fiscalPeriodRepository: FiscalPeriodRepository = TanvritKoin.get(),
    private val voucherRepository: VoucherRepository = TanvritKoin.get(),
) {
    private val voucherNetwork = VoucherNetwork.shared()

    suspend fun chargeNextPeriod(
        businessId: String,
        assetId: String,
    ): DepreciationChargeOutcome {
        if (businessId.isBlank()) return DepreciationChargeOutcome(assetId, null, null, "No active business")
        store.selectBusiness(businessId)
        val asset =
            store.assets.value.firstOrNull { it.id == assetId }
                ?: return DepreciationChargeOutcome(assetId, null, null, "Asset not found")

        val validationError = validateAsset(asset)
        if (validationError != null) return DepreciationChargeOutcome(assetId, null, null, validationError)

        val schedule = scheduleOf(asset)
        val nextRow =
            (0 until schedule.size).firstOrNull { it !in asset.postedRows }
                ?: return DepreciationChargeOutcome(assetId, null, null, "Fully depreciated")
        val row = schedule[nextRow]

        val today =
            Clock.System
                .now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date
                .toString()
        val referenceId = "depr:${asset.id}:$nextRow"

        // Idempotency: never double-draft the same row.
        val alreadyDrafted =
            runCatching {
                voucherRepository
                    .findByBusinessId(businessId, null, null, null, 0, 500)
                    .any { it.referenceId == referenceId }
            }.getOrDefault(false)
        if (alreadyDrafted) {
            // Cache knows the row — align the register and report the skip.
            store.update(asset.copy(postedRows = (asset.postedRows + nextRow).sorted()))
            return DepreciationChargeOutcome(assetId, nextRow, null, null)
        }

        val periodId = resolveOpenPeriodId(businessId, today)
        if (periodId.isBlank()) {
            return DepreciationChargeOutcome(assetId, null, null, "No open fiscal period contains $today")
        }

        val charge = majorFromMinor(row.chargeMinorUnits)
        val request =
            CreateVoucherRequest(
                businessId = businessId,
                voucherType = VoucherType.JOURNAL,
                date = today,
                narration = "Depreciation: ${asset.name} (period ${nextRow + 1})",
                referenceId = referenceId,
                lineItems =
                    listOf(
                        VoucherLineItem(
                            accountId = asset.depExpenseAccountId,
                            accountCode = asset.depExpenseAccountCode,
                            accountName = asset.depExpenseAccountName,
                            debit = Money.fromDouble(charge),
                            credit = Money.ZERO,
                            narration = "Depreciation charge — ${asset.name}",
                        ),
                        VoucherLineItem(
                            accountId = asset.accumDepAccountId,
                            accountCode = asset.accumDepAccountCode,
                            accountName = asset.accumDepAccountName,
                            debit = Money.ZERO,
                            credit = Money.fromDouble(charge),
                            narration = "Accumulated depreciation — ${asset.name}",
                        ),
                    ),
                fiscalPeriodId = periodId,
            )

        val created =
            voucherNetwork.createVoucherAsync(request).getOrNull()?.payload
                ?: return DepreciationChargeOutcome(assetId, null, null, "Server did not return a voucher")
        runCatching { voucherRepository.insert(created) }
        store.update(
            store.assets.value.first { it.id == asset.id }.copy(
                postedRows = (asset.postedRows + nextRow).sorted(),
            ),
        )
        return DepreciationChargeOutcome(assetId, nextRow, created.id, null)
    }

    /** Recomputed NBV (cost − charged rows) for display. */
    fun netBookValueMinorUnits(asset: FixedAsset): Long {
        val schedule = scheduleOf(asset)
        val charged = asset.postedRows.sumOf { rowIndex -> schedule.getOrNull(rowIndex)?.chargeMinorUnits ?: 0L }
        return (moneyMinorOf(asset.cost) - charged).coerceAtLeast(moneyMinorOf(asset.salvage))
    }

    fun scheduleOf(asset: FixedAsset): List<DepreciationRow> =
        runCatching {
            DepreciationMath.computeSchedule(
                costMinorUnits = moneyMinorOf(asset.cost),
                salvageMinorUnits = moneyMinorOf(asset.salvage.ifBlank { "0" }),
                method = DepreciationMethod.fromCodeOrDefault(asset.method),
                ratePercent = asset.ratePercent.trim().toDoubleOrNull() ?: 0.0,
                periods = DepreciationMath.MAX_ROWS_CAP,
            )
        }.getOrDefault(emptyList())

    private fun validateAsset(asset: FixedAsset): String? {
        if (asset.assetAccountId.isBlank()) return "Asset account missing"
        if (asset.depExpenseAccountId.isBlank()) return "Depreciation expense account missing"
        if (asset.accumDepAccountId.isBlank()) return "Accumulated depreciation account missing"
        if (moneyMinorOf(asset.cost) <= 0L) return "Cost must be positive"
        if ((asset.ratePercent.trim().toDoubleOrNull() ?: 0.0) <= 0.0) return "Rate must be positive"
        return null
    }

    private suspend fun resolveOpenPeriodId(
        businessId: String,
        date: String,
    ): String =
        runCatching { fiscalPeriodRepository.findByBusinessIdAndStatus(businessId, FiscalPeriodStatus.OPEN) }
            .getOrDefault(emptyList())
            .firstOrNull { it.startDate <= date && it.endDate >= date }
            ?.id
            ?: ""

    private fun moneyMinorOf(majorText: String): Long =
        ((majorText.trim().replace(",", "").toDoubleOrNull() ?: 0.0) * 100.0).let { DepreciationMath.roundHalfUp(it) }

    private fun majorFromMinor(minor: Long): Double = minor / 100.0
}
