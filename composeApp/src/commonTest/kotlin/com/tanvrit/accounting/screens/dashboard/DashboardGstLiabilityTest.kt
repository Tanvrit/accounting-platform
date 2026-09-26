package com.tanvrit.accounting.screens.dashboard

import com.tanvrit.core.feature.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pure derived state on [DashboardUiState] — GST liability aggregation
 * (`gstOutput - gstInput`). The VM-level trial-balance row filter that feeds
 * these two fields sits behind `ReportNetwork` on the Koin graph and is out
 * of commonTest reach; the arithmetic it feeds is pinned here.
 */
class DashboardGstLiabilityTest {
    @Test
    fun gstLiabilityIsOutputMinusInput() {
        val state =
            DashboardUiState(
                gstOutput = Money.fromDouble(118000.00),
                gstInput = Money.fromDouble(59000.00),
            )
        assertEquals(Money.fromDouble(59000.00), state.gstLiability)
    }

    @Test
    fun inputCreditExceedingOutputIsANegativeLiability() {
        // ITC credit carried forward shows as a negative liability.
        val state =
            DashboardUiState(
                gstOutput = Money.fromDouble(100.00),
                gstInput = Money.fromDouble(250.00),
            )
        assertEquals(Money.fromDouble(-150.00), state.gstLiability)
    }

    @Test
    fun balancedInputOutputIsZeroLiability() {
        val state =
            DashboardUiState(
                gstOutput = Money.fromDouble(1234.56),
                gstInput = Money.fromDouble(1234.56),
            )
        assertEquals(Money.ZERO, state.gstLiability)
    }

    @Test
    fun defaultStateHasZeroFiguresAndEmptyLists() {
        val state = DashboardUiState()
        assertEquals(Money.ZERO, state.gstLiability)
        assertEquals(Money.ZERO, state.cashPosition)
        assertEquals(Money.ZERO, state.netProfit)
        assertTrue(state.topExpenses.isEmpty())
        assertTrue(state.recentVouchers.isEmpty())
    }
}
