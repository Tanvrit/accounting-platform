package com.tanvrit.accounting.screens.voucherEntry

import com.tanvrit.core.feature.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Double-entry balancing of the voucher editor, exercised through
 * [VoucherEntryUiState]'s computed `totalDebit` / `totalCredit` / `isBalanced` —
 * the same getters the screen and `VoucherEntryViewModel.submit` gate on.
 *
 * Coverage gap (deliberate): `VoucherEntryViewModel.validate()` and `submit()`
 * are NOT reachable from commonTest — the VM's property initializers resolve
 * `AccountingWorkspace` / repositories / `VoucherNetwork.shared()` from
 * TanvritKoin, which needs the full SDK Koin graph. What is tested here is the
 * balancing predicate those private paths delegate to; the account-count and
 * header rules inside `validate()` have no commonTest seam.
 */
class VoucherBalancingTest {
    private var nextLocalId = 1

    private fun line(
        debit: String = "",
        credit: String = "",
    ) = VoucherLineDraft(localId = "line-${nextLocalId++}", debitText = debit, creditText = credit)

    @Test
    fun emptyLineListIsNotBalanced() {
        val state = VoucherEntryUiState(lines = emptyList())
        assertEquals(Money.ZERO, state.totalDebit)
        assertEquals(Money.ZERO, state.totalCredit)
        assertFalse(state.isBalanced)
    }

    @Test
    fun balancedTwoLegDraftIsAccepted() {
        val state =
            VoucherEntryUiState(
                lines = listOf(line(debit = "12500.50"), line(credit = "12500.50")),
            )
        assertEquals(Money.fromDouble(12500.50), state.totalDebit)
        assertEquals(Money.fromDouble(12500.50), state.totalCredit)
        assertTrue(state.isBalanced)
    }

    @Test
    fun multiLegDraftsSumPerSide() {
        val state =
            VoucherEntryUiState(
                lines =
                    listOf(
                        line(debit = "250.25"),
                        line(debit = "749.75"),
                        line(credit = "1000.00"),
                    ),
            )
        assertEquals(Money.fromDouble(1000.00), state.totalDebit)
        assertEquals(Money.fromDouble(1000.00), state.totalCredit)
        assertTrue(state.isBalanced)
    }

    @Test
    fun imbalancedDraftIsRejected() {
        val state =
            VoucherEntryUiState(
                lines = listOf(line(debit = "1000.00"), line(credit = "999.99")),
            )
        assertFalse(state.isBalanced)
    }

    @Test
    fun zeroTotalDraftIsRejected() {
        // isBalanced requires a positive total, not merely equal sides.
        val blankLines = VoucherEntryUiState(lines = listOf(line(), line()))
        assertFalse(blankLines.isBalanced)
        val explicitZeros =
            VoucherEntryUiState(
                lines = listOf(line(debit = "0.00"), line(credit = "0.00")),
            )
        assertFalse(explicitZeros.isBalanced)
    }

    @Test
    fun indianGroupingAndPlainAmountsBalance() {
        val state =
            VoucherEntryUiState(
                lines = listOf(line(debit = "1,25,000.00"), line(credit = "125000.00")),
            )
        assertTrue(state.isBalanced)
        assertEquals(Money.fromDouble(125000.00), state.totalDebit)
    }

    @Test
    fun whitespacePaddedAmountsAreParsed() {
        val state =
            VoucherEntryUiState(
                lines = listOf(line(debit = " 42.50 "), line(credit = "42.5")),
            )
        assertTrue(state.isBalanced)
    }

    @Test
    fun unparsableAmountsCountAsZero() {
        val state =
            VoucherEntryUiState(
                lines = listOf(line(debit = "abc"), line(credit = "")),
            )
        assertEquals(Money.ZERO, state.totalDebit)
        assertEquals(Money.ZERO, state.totalCredit)
        assertFalse(state.isBalanced)
    }

    @Test
    fun equalNegativeSidesAreRejected() {
        // Pin: isBalanced also demands totalDebit > ZERO, so a draft balanced
        // around a negative total (credit-note style entry) is refused.
        val state =
            VoucherEntryUiState(
                lines = listOf(line(debit = "-50.00"), line(credit = "-50.00")),
            )
        assertEquals(state.totalDebit, state.totalCredit)
        assertFalse(state.isBalanced)
    }

    @Test
    fun minorUnitSummationAbsorbsFloatDrift() {
        // 0.10 + 0.20 would drift in Double space; Money rounds each leg to
        // paise first, so the draft balances exactly.
        val state =
            VoucherEntryUiState(
                lines = listOf(line(debit = "0.10"), line(debit = "0.20"), line(credit = "0.30")),
            )
        assertTrue(state.isBalanced)
    }
}
