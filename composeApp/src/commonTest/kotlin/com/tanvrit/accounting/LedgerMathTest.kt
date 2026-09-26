package com.tanvrit.accounting

import com.tanvrit.accounting.screens.ledger.LedgerMath
import com.tanvrit.accounting.screens.ledger.toDrCr
import com.tanvrit.core.feature.accounting.model.Voucher
import com.tanvrit.core.feature.accounting.model.VoucherLineItem
import com.tanvrit.core.feature.accounting.model.VoucherStatus
import com.tanvrit.core.feature.accounting.model.VoucherType
import com.tanvrit.core.feature.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals

class LedgerMathTest {
    /** Minor units straight from paise — no Double rounding in fixtures. */
    private fun money(rupees: Long): Money = Money.fromSmallestUnit(rupees * 100)

    private fun voucher(
        id: String,
        number: String,
        date: String,
        accountId: String = "acc-1",
        debit: Money = Money.ZERO,
        credit: Money = Money.ZERO,
        status: VoucherStatus = VoucherStatus.POSTED,
        narration: String = "",
    ): Voucher {
        val line = VoucherLineItem(accountId = accountId, accountCode = "1000", accountName = "Cash", debit = debit, credit = credit)
        // Balancing contra-leg on a different account so the fixture voucher balances.
        val contra = VoucherLineItem(accountId = "acc-contra", accountCode = "9000", accountName = "Contra", debit = credit, credit = debit)
        return Voucher(
            businessId = "biz-1",
            voucherNumber = number,
            voucherType = VoucherType.JOURNAL,
            date = date,
            narration = narration,
            status = status,
            lineItems = listOf(line, contra),
        ).also { it.id = id }
    }

    @Test
    fun runningBalanceIsDebitPositiveMatchingTrialBalance() {
        val entries =
            LedgerMath.entriesForAccount(
                listOf(
                    voucher(id = "v-1", number = "JV-1", date = "2025-04-05", debit = money(100)),
                    voucher(id = "v-2", number = "JV-2", date = "2025-04-07", credit = money(40)),
                ),
                "acc-1",
            )
        val lines = LedgerMath.linesInRange(entries, fromDate = "", toDate = "", opening = Money.ZERO)
        assertEquals(money(100), lines[0].runningBalance)
        assertEquals(money(60), lines[1].runningBalance)
        assertEquals(money(60), LedgerMath.closingOf(Money.ZERO, lines))
    }

    @Test
    fun sortsAscendingByDateWithDeterministicSameDayOrder() {
        val entries =
            LedgerMath.entriesForAccount(
                listOf(
                    voucher(id = "v-2", number = "JV-9", date = "2025-04-07", debit = money(10)),
                    voucher(id = "v-1", number = "JV-2", date = "2025-04-05", debit = money(10)),
                    voucher(id = "v-3", number = "JV-1", date = "2025-04-05", debit = money(10)),
                ),
                "acc-1",
            )
        val sorted = LedgerMath.sortAscending(entries)
        assertEquals(listOf("JV-1", "JV-2", "JV-9"), sorted.map { it.voucherNumber })
    }

    @Test
    fun onlyPostedVouchersAffectTheLedger() {
        val entries =
            LedgerMath.entriesForAccount(
                listOf(
                    voucher(id = "v-1", number = "JV-1", date = "2025-04-05", debit = money(100)),
                    voucher(id = "v-2", number = "JV-2", date = "2025-04-06", debit = money(50), status = VoucherStatus.DRAFT),
                    voucher(id = "v-3", number = "JV-3", date = "2025-04-07", debit = money(70), status = VoucherStatus.CANCELLED),
                ),
                "acc-1",
            )
        assertEquals(listOf("JV-1"), LedgerMath.sortAscending(entries).map { it.voucherNumber })
    }

    @Test
    fun carryFromPriorPeriodBecomesOpeningOfTheWindow() {
        val entries =
            LedgerMath.entriesForAccount(
                listOf(
                    // Prior FY period (before the window)
                    voucher(id = "v-1", number = "JV-1", date = "2025-03-10", debit = money(100)),
                    voucher(id = "v-2", number = "JV-2", date = "2025-03-20", credit = money(40)),
                    // Inside the window
                    voucher(id = "v-3", number = "JV-3", date = "2025-04-05", debit = money(50)),
                    voucher(id = "v-4", number = "JV-4", date = "2025-04-07", credit = money(120)),
                ),
                "acc-1",
            )
        val opening = LedgerMath.openingAt(openingBalance = money(250), entries = entries, fromDate = "2025-04-01")
        // 250 + (100 − 40) carried from the prior period
        assertEquals(money(310), opening)
        val lines = LedgerMath.linesInRange(entries, fromDate = "2025-04-01", toDate = "2025-04-30", opening = opening)
        assertEquals(2, lines.size)
        assertEquals(money(360), lines[0].runningBalance)
        assertEquals(money(240), lines[1].runningBalance)
        assertEquals(money(240), LedgerMath.closingOf(opening, lines))
    }

    @Test
    fun blankFromDateMeansNoCarry() {
        val entries =
            LedgerMath.entriesForAccount(
                listOf(voucher(id = "v-1", number = "JV-1", date = "2025-03-10", debit = money(100))),
                "acc-1",
            )
        assertEquals(money(250), LedgerMath.openingAt(money(250), entries, fromDate = ""))
    }

    @Test
    fun emptyWindowClosesAtItsOpening() {
        assertEquals(money(42), LedgerMath.closingOf(opening = money(42), lines = emptyList()))
    }

    @Test
    fun toDrCrReportsTheSideOfTheBalance() {
        assertEquals(money(60) to "Dr", money(60).toDrCr())
        assertEquals(money(60) to "Cr", (-money(60)).toDrCr())
        assertEquals(Money.ZERO to "Dr", Money.ZERO.toDrCr())
    }

    @Test
    fun multiLegVoucherContributesOneEntryPerAccountLeg() {
        val entries =
            LedgerMath.entriesForAccount(
                listOf(
                    Voucher(
                        businessId = "biz-1",
                        voucherNumber = "JV-7",
                        voucherType = VoucherType.JOURNAL,
                        date = "2025-04-09",
                        status = VoucherStatus.POSTED,
                        lineItems =
                            listOf(
                                VoucherLineItem(accountId = "acc-1", debit = money(10), narration = "first leg"),
                                VoucherLineItem(accountId = "acc-1", debit = money(5), narration = "second leg"),
                                VoucherLineItem(accountId = "acc-2", credit = money(15)),
                            ),
                    ),
                ),
                "acc-1",
            )
        assertEquals(2, entries.size)
        // Same voucher, same date: leg order is the entryId tiebreak — assert the set, not the order.
        assertEquals(setOf("first leg", "second leg"), entries.map { it.narration }.toSet())
        assertEquals(money(15), entries.fold(Money.ZERO) { acc, entry -> acc + LedgerMath.signedDelta(entry) })
    }
}
