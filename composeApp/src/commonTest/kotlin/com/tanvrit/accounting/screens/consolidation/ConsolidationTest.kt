package com.tanvrit.accounting.screens.consolidation

import com.tanvrit.core.feature.accounting.model.AccountBalance
import com.tanvrit.core.feature.accounting.model.BalanceSheetReport
import com.tanvrit.core.feature.accounting.model.CashFlowReport
import com.tanvrit.core.feature.accounting.model.ConsolidatedReport
import com.tanvrit.core.feature.accounting.model.ConsolidationGroup
import com.tanvrit.core.feature.accounting.model.EliminationEntry
import com.tanvrit.core.feature.accounting.model.ProfitAndLossReport
import com.tanvrit.core.feature.accounting.model.TrialBalanceReport
import com.tanvrit.core.feature.accounting.model.TrialBalanceRow
import com.tanvrit.core.feature.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pure-helper coverage for the consolidation viewer (roadmap #4): member-id
 * CSV parsing, group summaries, the ConsolidatedReport → sections mapping and
 * elimination-form validation. Nothing here touches Compose, Koin or the
 * network.
 */
class ConsolidationTest {
    // --- parseMemberBusinessIds ---

    @Test
    fun parseMemberBusinessIdsTrimsSplitsAndKeepsOrder() {
        assertEquals(
            listOf("biz-a", "biz-b", "biz-c"),
            parseMemberBusinessIds(" biz-a , biz-b,, biz-c "),
        )
    }

    @Test
    fun parseMemberBusinessIdsAcceptsNewlinesAndSemicolons() {
        assertEquals(
            listOf("biz-a", "biz-b", "biz-c"),
            parseMemberBusinessIds("biz-a\nbiz-b;biz-c"),
        )
    }

    @Test
    fun parseMemberBusinessIdsDropsBlanksAndDeduplicatesFirstSeen() {
        assertEquals(listOf("biz-a", "biz-b"), parseMemberBusinessIds("biz-a, biz-b, biz-a"))
        assertEquals(emptyList(), parseMemberBusinessIds(" , ,\n"))
        assertEquals(emptyList(), parseMemberBusinessIds(""))
    }

    // --- validateGroupForm ---

    @Test
    fun validateGroupFormRejectsBlankNameAndEmptyMembers() {
        assertEquals("Group name is required", validateGroupForm(" ", listOf("biz-a")))
        assertEquals("Add at least one subsidiary business id", validateGroupForm("Group A", emptyList()))
        assertNull(validateGroupForm("Group A", listOf("biz-a")))
    }

    // --- memberIds / groupSummary ---

    @Test
    fun memberIdsPutsParentFirstAndDeduplicatesBlanks() {
        val group =
            ConsolidationGroup(
                businessId = "biz-parent",
                name = "Holdings",
                parentBusinessId = "biz-parent",
                subsidiaryBusinessIds = listOf("biz-sub-1", "biz-parent", "", "biz-sub-2", "biz-sub-1"),
            )
        assertEquals(listOf("biz-parent", "biz-sub-1", "biz-sub-2"), memberIds(group))
    }

    @Test
    fun groupSummaryShowsMethodMembersAndInactiveFlag() {
        val active =
            groupSummary(
                ConsolidationGroup(
                    businessId = "biz-parent",
                    name = "Holdings",
                    parentBusinessId = "biz-parent",
                    subsidiaryBusinessIds = listOf("biz-sub-1", "biz-sub-2"),
                    method = "PROPORTIONATE",
                    isActive = true,
                ),
            )
        assertEquals("Holdings", active.name)
        assertEquals(3, active.memberCount)
        assertEquals("PROPORTIONATE · 3 member(s)", active.detail)

        val inactive =
            groupSummary(
                ConsolidationGroup(businessId = "biz-parent", name = " ", method = "", isActive = false),
            )
        assertEquals("(unnamed group)", inactive.name)
        assertEquals(0, inactive.memberCount)
        assertEquals("0 member(s) · inactive", inactive.detail)
    }

    // --- reportSections ---

    @Test
    fun reportSectionsMapsEverySubReportInOrderWithTotalsEmphasized() {
        val report =
            ConsolidatedReport(
                businessId = "biz-parent",
                groupId = "grp-1",
                fiscalPeriodId = "fp-1",
                trialBalance =
                    TrialBalanceReport(
                        businessId = "biz-parent",
                        asOfDate = "2026-03-31",
                        rows =
                            listOf(
                                // Net presentation: debit − credit.
                                TrialBalanceRow(
                                    accountId = "acc-cash",
                                    accountCode = "1000",
                                    accountName = "Cash",
                                    debit = Money.fromDouble(10.0),
                                    credit = Money.fromDouble(2.5),
                                ),
                                TrialBalanceRow(
                                    accountId = "acc-cap",
                                    accountCode = "",
                                    accountName = "Capital",
                                    debit = Money.ZERO,
                                    credit = Money.fromDouble(7.5),
                                ),
                            ),
                        totalDebit = Money.fromDouble(10.0),
                        totalCredit = Money.fromDouble(10.0),
                    ),
                profitAndLoss =
                    ProfitAndLossReport(
                        businessId = "biz-parent",
                        fromDate = "2026-04-01",
                        toDate = "2026-03-31",
                        totalRevenue = Money.fromDouble(100.0),
                        totalExpenses = Money.fromDouble(40.0),
                        netProfit = Money.fromDouble(60.0),
                        revenueAccounts =
                            listOf(
                                AccountBalance(
                                    accountId = "acc-sales",
                                    accountCode = "4000",
                                    accountName = "Sales",
                                    balance = Money.fromDouble(100.0),
                                ),
                            ),
                        expenseAccounts =
                            listOf(
                                AccountBalance(
                                    accountId = "acc-rent",
                                    accountCode = "",
                                    accountName = "Rent",
                                    balance = Money.fromDouble(40.0),
                                ),
                            ),
                    ),
                balanceSheet =
                    BalanceSheetReport(
                        businessId = "biz-parent",
                        totalAssets = Money.fromDouble(200.0),
                        totalLiabilities = Money.fromDouble(80.0),
                        totalEquity = Money.fromDouble(120.0),
                    ),
                cashFlow =
                    CashFlowReport(
                        businessId = "biz-parent",
                        method = "",
                        operatingCashFlow = Money.fromDouble(50.0),
                        netCashFlow = Money.fromDouble(50.0),
                        closingCash = Money.fromDouble(150.0),
                    ),
                minorityInterest = Money.fromDouble(12.0),
            )

        val sections = reportSections(report)
        assertEquals(
            listOf("Trial balance · as of 2026-03-31", "Profit & loss", "Balance sheet", "Cash flow · INDIRECT"),
            sections.map { it.title },
        )

        val tb = sections[0]
        assertEquals("Cash (1000)", tb.rows[0].label)
        assertEquals(Money.fromDouble(7.5), tb.rows[0].amount)
        assertEquals("Capital", tb.rows[1].label)
        assertEquals(Money.fromDouble(-7.5), tb.rows[1].amount)
        assertEquals(Money.fromDouble(10.0), tb.rows[2].amount)
        assertEquals(Money.fromDouble(10.0), tb.rows[3].amount)
        assertTrue(tb.rows[2].emphasis)

        val pnl = sections[1]
        assertEquals(Money.fromDouble(100.0), pnl.rows[1].amount) // total revenue
        assertEquals(Money.fromDouble(40.0), pnl.rows[3].amount) // total expenses
        assertEquals(Money.fromDouble(60.0), pnl.rows[4].amount) // net profit

        val bs = sections[2]
        assertEquals(Money.fromDouble(200.0), bs.rows[0].amount)
        assertEquals(Money.fromDouble(80.0), bs.rows[1].amount)
        assertEquals(Money.fromDouble(120.0), bs.rows[2].amount)

        val cf = sections[3]
        assertEquals(Money.fromDouble(50.0), cf.rows[4].amount) // net cash flow
        assertEquals(Money.fromDouble(150.0), cf.rows[5].amount) // closing cash
    }

    @Test
    fun reportSectionsOmitsAbsentSubReports() {
        assertEquals(emptyList(), reportSections(ConsolidatedReport()))
        val onlyPnl = reportSections(ConsolidatedReport(profitAndLoss = ProfitAndLossReport(netProfit = Money.fromDouble(1.0))))
        assertEquals(listOf("Profit & loss"), onlyPnl.map { it.title })
        assertFalse(onlyPnl[0].rows.isEmpty())
    }

    // --- eliminateTotal ---

    @Test
    fun eliminationTotalSumsMoney() {
        assertEquals(Money.ZERO, eliminationTotal(emptyList()))
        val entries =
            listOf(
                EliminationEntry(amount = Money.fromDouble(100.0)),
                EliminationEntry(amount = Money.fromDouble(2.5)),
                EliminationEntry(amount = Money.fromDouble(-50.0)),
            )
        assertEquals(Money.fromDouble(52.5), eliminationTotal(entries))
    }

    // --- validateEliminationForm / eliminationDescription ---

    @Test
    fun validateEliminationFormChecksAccountsAndAmount() {
        assertEquals(
            "Pick a debit account",
            validateEliminationForm("", "acc-b", Money.fromDouble(10.0)),
        )
        assertEquals(
            "Pick a credit account",
            validateEliminationForm("acc-a", "", Money.fromDouble(10.0)),
        )
        assertEquals(
            "Debit and credit accounts must differ",
            validateEliminationForm("acc-a", "acc-a", Money.fromDouble(10.0)),
        )
        assertEquals(
            "Enter an amount above zero",
            validateEliminationForm("acc-a", "acc-b", Money.ZERO),
        )
        assertNull(validateEliminationForm("acc-a", "acc-b", Money.fromDouble(1.0)))
    }

    @Test
    fun eliminationDescriptionFoldsTheLinkedVoucherIntoTheDescription() {
        assertEquals("Intercompany rent", eliminationDescription("Intercompany rent", ""))
        assertEquals("Linked voucher v-9", eliminationDescription(" ", " v-9 "))
        assertEquals("Rent [linked voucher v-9]", eliminationDescription("Rent", "v-9"))
    }
}
