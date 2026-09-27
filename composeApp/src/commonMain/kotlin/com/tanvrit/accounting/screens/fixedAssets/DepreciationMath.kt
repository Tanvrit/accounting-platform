package com.tanvrit.accounting.screens.fixedAssets

/**
 * Pure depreciation math (roadmap #6). All amounts are minor-unit Longs. Both
 * methods charge monthly rows; the register stops emitting once book value
 * reaches salvage.
 */
enum class DepreciationMethod(
    val code: String,
    val label: String,
) {
    SLM("SLM", "Straight line"),
    WDV("WDV", "Written-down value"),
    ;

    companion object {
        fun fromCodeOrDefault(code: String): DepreciationMethod = entries.firstOrNull { it.code == code } ?: WDV

        fun fromLabelOrDefault(label: String): DepreciationMethod = entries.firstOrNull { it.label == label } ?: WDV
    }
}

/** One monthly depreciation row in an asset's schedule. */
data class DepreciationRow(
    val index: Int,
    val chargeMinorUnits: Long,
    val closingMinorUnits: Long,
)

object DepreciationMath {
    /**
     * Monthly schedule rows for an asset.
     *
     * - SLM: monthly charge = (cost − salvage) × rate% ÷ 100 ÷ 12 — constant
     *   until the book value would cross salvage; the last row is the remainder.
     * - WDV: monthly charge = opening × (rate% ÷ 100 ÷ 12) — declining.
     * Both stop (and the final row is clipped) at [salvageMinorUnits].
     *
     * Rounding: half-up at minor-unit granularity per row; the tail adjustment
     * lands in the last row so the schedule always sums exactly to
     * (cost − salvage).
     */
    fun computeSchedule(
        costMinorUnits: Long,
        salvageMinorUnits: Long,
        method: DepreciationMethod,
        ratePercent: Double,
        periods: Int,
    ): List<DepreciationRow> {
        require(costMinorUnits >= salvageMinorUnits) { "Cost below salvage" }
        require(ratePercent > 0.0) { "Rate must be positive" }
        if (periods <= 0 || costMinorUnits == salvageMinorUnits) return emptyList()

        val depreciable = (costMinorUnits - salvageMinorUnits).toDouble()
        val monthlyRate = ratePercent / 100.0 / 12.0
        val rows = ArrayList<DepreciationRow>(periods)
        var bookValue = costMinorUnits
        var chargedCumulative = 0.0

        for (index in 0 until periods) {
            if (bookValue <= salvageMinorUnits) break
            val charge =
                when (method) {
                    // Cumulative-target method so rounding never drifts the total.
                    DepreciationMethod.SLM -> {
                        val targetCumulative = depreciable * (index + 1) / idealSlmPeriods(costMinorUnits, salvageMinorUnits, ratePercent)
                        roundHalfUp(targetCumulative - chargedCumulative)
                    }
                    DepreciationMethod.WDV -> roundHalfUp(bookValue * monthlyRate)
                }
            if (charge <= 0L) break
            val clipped = minOf(charge, bookValue - salvageMinorUnits)
            bookValue -= clipped
            chargedCumulative += charge
            rows += DepreciationRow(index = index, chargeMinorUnits = clipped, closingMinorUnits = bookValue)
        }
        return rows
    }

    /** Nominal SLM life in months for the rate (annual charge = cost × rate%). */
    fun idealSlmPeriods(
        costMinorUnits: Long,
        salvageMinorUnits: Long,
        ratePercent: Double,
    ): Int {
        if (costMinorUnits <= salvageMinorUnits || ratePercent <= 0.0) return 1
        val depreciable = (costMinorUnits - salvageMinorUnits).toDouble()
        val annual = costMinorUnits.toDouble() * ratePercent / 100.0
        if (annual <= 0.0) return MAX_ROWS_CAP
        return kotlin.math.max(1, kotlin.math.ceil(depreciable / annual * 12.0).toInt())
    }

    fun roundHalfUp(value: Double): Long = if (value <= 0.0) 0L else kotlin.math.floor(value + 0.5).toLong()

    const val MAX_ROWS_CAP = 600 // 50 years monthly — enough headroom for any real asset
}
