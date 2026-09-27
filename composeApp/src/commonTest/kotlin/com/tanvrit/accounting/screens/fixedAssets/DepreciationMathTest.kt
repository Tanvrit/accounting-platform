package com.tanvrit.accounting.screens.fixedAssets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DepreciationMathTest {
    @Test
    fun `slm charges equal monthly amounts inside the computed life`() {
        // ₹1,20,000 asset, ₹0 salvage, 20% SLM → annual ₹24,000 → life 5y = 60
        // months; monthly = ₹2,000 = 200_000 minor units.
        val rows = DepreciationMath.computeSchedule(12_000_000, 0, DepreciationMethod.SLM, 20.0, 60)
        assertEquals(60, rows.size)
        assertTrue(rows.all { it.chargeMinorUnits == 2_00_000L })
        assertEquals(0L, rows.last().closingMinorUnits)
    }

    @Test
    fun `wdv declines and sums to cost minus salvage within the cap`() {
        // ₹10,000 = 1_000_000 minor, salvage ₹1, 45% WDV.
        val rows = DepreciationMath.computeSchedule(1_000_000, 100, DepreciationMethod.WDV, 45.0, 600)
        assertTrue(rows.isNotEmpty())
        assertEquals(1_000_000 - 100, rows.sumOf { it.chargeMinorUnits })
        assertEquals(100L, rows.last().closingMinorUnits)
        assertTrue(rows.first().chargeMinorUnits > rows.last().chargeMinorUnits)
    }

    @Test
    fun `salvage floor is never crossed`() {
        // ₹100 asset, ₹80 salvage, 90% WDV — aggressively converging on the floor.
        val rows = DepreciationMath.computeSchedule(10_000, 8_000, DepreciationMethod.WDV, 90.0, 100)
        assertTrue(rows.all { it.closingMinorUnits >= 8_000 })
        assertEquals(2_000L, rows.sumOf { it.chargeMinorUnits })
    }

    @Test
    fun `periods boundary — no rows past requested count or salvage`() {
        // ₹1,000 asset, 10% WDV, ask for 5 rows only.
        val rows = DepreciationMath.computeSchedule(100_000, 0, DepreciationMethod.WDV, 10.0, 5)
        assertEquals(5, rows.size)
        assertTrue(DepreciationMath.computeSchedule(100_000, 0, DepreciationMethod.WDV, 10.0, 0).isEmpty())
        assertTrue(DepreciationMath.computeSchedule(100_000, 100_000, DepreciationMethod.SLM, 10.0, 60).isEmpty())
    }

    @Test
    fun `input validation is loud, not silent`() {
        assertFailsWith<IllegalArgumentException> { DepreciationMath.computeSchedule(5_000, 10_000, DepreciationMethod.SLM, 20.0, 10) }
        assertFailsWith<IllegalArgumentException> { DepreciationMath.computeSchedule(10_000, 0, DepreciationMethod.SLM, 0.0, 10) }
    }

    @Test
    fun `rounding is half-up at minor units`() {
        assertEquals(1L, DepreciationMath.roundHalfUp(0.5))
        assertEquals(2L, DepreciationMath.roundHalfUp(1.5))
        assertEquals(2L, DepreciationMath.roundHalfUp(2.49))
        assertEquals(0L, DepreciationMath.roundHalfUp(0.0))
        assertEquals(1L, DepreciationMath.roundHalfUp(1.0))
    }
}
