package com.tanvrit.accounting.screens.tdsCenter

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * TDS quarter / financial-year defaults.
 *
 * `currentQuarter()` / `currentFinancialYear()` are `private` top-level fns in
 * TdsCenterViewModel.kt, so this class covers them two ways:
 *  1. `stateDefaultsMatchReplicaForToday` exercises the REAL functions through
 *     the observable [TdsCenterUiState] constructor defaults — this is what
 *     keeps the replicas honest (a production change breaks it the same day).
 *  2. `quarterFor` / `financialYearFor` below are small pure REPLICAS of that
 *     private code — **keep in sync with TdsCenterViewModel.kt** (the two
 *     private fns at the bottom of the file). They exist so the full twelve-
 *     month mapping is testable, which the clock-bound originals are not.
 */
class TdsDateLogicTest {
    // KEEP IN SYNC: TdsCenterViewModel.kt `currentQuarter()`.
    private fun quarterFor(month: Int): String = "Q${((month - 4 + 12) % 12) / 3 + 1}"

    // KEEP IN SYNC: TdsCenterViewModel.kt `currentFinancialYear()`.
    private fun financialYearFor(
        year: Int,
        month: Int,
    ): String {
        val start = if (month >= 4) year else year - 1
        return "$start-" + ((start + 1) % 100).toString().padStart(2, '0')
    }

    private fun today() =
        Clock.System
            .now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date

    @Test
    fun quarterMappingCoversAllTwelveMonths() {
        // Indian FY quarters: Q1 = Apr–Jun, Q2 = Jul–Sep, Q3 = Oct–Dec, Q4 = Jan–Mar.
        listOf(4, 5, 6).forEach { month -> assertEquals("Q1", quarterFor(month), "month $month") }
        listOf(7, 8, 9).forEach { month -> assertEquals("Q2", quarterFor(month), "month $month") }
        listOf(10, 11, 12).forEach { month -> assertEquals("Q3", quarterFor(month), "month $month") }
        listOf(1, 2, 3).forEach { month -> assertEquals("Q4", quarterFor(month), "month $month") }
    }

    @Test
    fun financialYearMappingSpansAprilToMarch() {
        assertEquals("2026-27", financialYearFor(2026, 4))
        assertEquals("2026-27", financialYearFor(2026, 6))
        assertEquals("2026-27", financialYearFor(2026, 12))
        assertEquals("2026-27", financialYearFor(2027, 1))
        assertEquals("2026-27", financialYearFor(2027, 3))
        assertEquals("2025-26", financialYearFor(2026, 3))
        // Pinned as-is: the century boundary renders FY1999-2000 as "1999-00".
        assertEquals("1999-00", financialYearFor(1999, 4))
    }

    @Test
    fun stateDefaultsMatchReplicaForToday() {
        // Reads the clock once on either side of construction so a midnight
        // rollover mid-test cannot flake: the VM's value must match the
        // replica for one of the two observed dates.
        val before = today()
        val state = TdsCenterUiState()
        val after = today()
        val acceptable =
            listOf(before, after)
                .map { quarterFor(it.month.ordinal + 1) to financialYearFor(it.year, it.month.ordinal + 1) }
                .toSet()
        assertTrue(
            (state.quarter to state.financialYear) in acceptable,
            "state=${state.quarter}/${state.financialYear} expected one of $acceptable",
        )
    }

    @Test
    fun tdsUiStateDefaultsAreRegistrationNeutral() {
        val state = TdsCenterUiState()
        assertEquals("26Q", state.returnType)
        assertEquals("194J", state.certificateSection)
        assertEquals(TdsTab.RETURNS, state.activeTab)
        assertEquals("", state.tan)
        assertTrue(state.quarter.matches(Regex("Q[1-4]")), "quarter='${state.quarter}'")
        assertTrue(state.financialYear.matches(Regex("\\d{4}-\\d{2}")), "fy='${state.financialYear}'")
    }
}
