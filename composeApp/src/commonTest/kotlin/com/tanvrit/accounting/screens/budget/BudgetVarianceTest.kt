package com.tanvrit.accounting.screens.budget

import com.tanvrit.core.feature.accounting.model.AccountingBudget
import com.tanvrit.core.feature.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pure computed state on the Budget screen — [BudgetVarianceRow.variance] /
 * [BudgetVarianceRow.variancePercent] and [BudgetUiState] totals. The VM's
 * network-filled inputs are out of commonTest reach (Koin-bound); the math
 * they feed is pinned here.
 */
class BudgetVarianceTest {
    private fun row(
        budgeted: Double,
        actual: Double,
    ) = BudgetVarianceRow(
        accountCode = "6100",
        accountName = "Rent",
        budgeted = Money.fromDouble(budgeted),
        actual = Money.fromDouble(actual),
    )

    @Test
    fun varianceIsBudgetedMinusActual() {
        assertEquals(Money.fromDouble(250.00), row(1000.00, 750.00).variance)
        assertEquals(Money.fromDouble(-250.00), row(1000.00, 1250.00).variance)
    }

    @Test
    fun variancePercentIsSignedAndScaled() {
        assertEquals(25.0, row(1000.00, 750.00).variancePercent)
        assertEquals(-25.0, row(1000.00, 1250.00).variancePercent)
    }

    @Test
    fun zeroBudgetYieldsZeroPercentNotNaN() {
        // Pinned branch: budgeted == ZERO short-circuits the division.
        assertEquals(0.0, row(0.00, 500.00).variancePercent)
        assertEquals(0.0, row(0.00, 0.00).variancePercent)
    }

    @Test
    fun uiStateTotalsFoldAcrossBudgetsAndVarianceRows() {
        val state =
            BudgetUiState(
                budgets =
                    listOf(
                        AccountingBudget(budgetedAmount = Money.fromDouble(1000.00)),
                        AccountingBudget(budgetedAmount = Money.fromDouble(500.00)),
                    ),
                varianceRows = listOf(row(1000.00, 600.00), row(500.00, 900.00)),
            )
        assertEquals(Money.fromDouble(1500.00), state.totalBudgeted)
        assertEquals(Money.fromDouble(1500.00), state.totalActual)
        assertEquals(Money.ZERO, state.totalVariance)
    }
}
