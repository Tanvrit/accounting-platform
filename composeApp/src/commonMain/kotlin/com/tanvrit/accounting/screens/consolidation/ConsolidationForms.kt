package com.tanvrit.accounting.screens.consolidation

import com.tanvrit.core.feature.accounting.model.AccountBalance
import com.tanvrit.core.feature.accounting.model.ConsolidatedReport
import com.tanvrit.core.feature.accounting.model.ConsolidationGroup
import com.tanvrit.core.feature.accounting.model.EliminationEntry
import com.tanvrit.core.feature.money.Money

// Pure form/report helpers for the group-consolidation viewer (roadmap #4).
// Common-source-set safe, no Compose, no I/O — covered directly by
// `commonTest` `ConsolidationTest`.

/**
 * Splits the "Business IDs, comma-separated" textarea into ids: split on
 * comma / newline / semicolon, trim, drop blanks, keep first-seen order,
 * distinct.
 */
fun parseMemberBusinessIds(raw: String): List<String> =
    raw
        .split(',', '\n', ';')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

/** Validation error for the new-group sheet, or null when it is submittable. */
fun validateGroupForm(
    name: String,
    memberIds: List<String>,
): String? =
    when {
        name.isBlank() -> "Group name is required"
        memberIds.isEmpty() -> "Add at least one subsidiary business id"
        else -> null
    }

/**
 * The group's flattened member list for the card chips: the parent business
 * first, then the subsidiaries, trimmed, non-blank and distinct (a parent
 * repeated in the subsidiary list is shown once).
 */
fun memberIds(group: ConsolidationGroup): List<String> =
    (listOf(group.parentBusinessId) + group.subsidiaryBusinessIds)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

/** Flattened, already-derived card copy for one consolidation group. */
data class GroupSummary(
    val name: String,
    val memberCount: Int,
    /** e.g. "FULL · 3 member(s)" plus " · inactive" when the group is off. */
    val detail: String,
)

fun groupSummary(group: ConsolidationGroup): GroupSummary {
    val members = memberIds(group)
    val detail =
        buildList {
            if (group.method.isNotBlank()) add(group.method)
            add("${members.size} member(s)")
            if (!group.isActive) add("inactive")
        }.joinToString(" · ")
    return GroupSummary(
        name = group.name.ifBlank { "(unnamed group)" },
        memberCount = members.size,
        detail = detail,
    )
}

/** One renderable amount row; [emphasis] marks totals/net rows for bolding. */
data class ReportRow(
    val label: String,
    val amount: Money,
    val emphasis: Boolean = false,
)

data class ReportSection(
    val title: String,
    val rows: List<ReportRow>,
)

private fun accountRows(accounts: List<AccountBalance>): List<ReportRow> =
    accounts.map { account ->
        ReportRow(
            label =
                if (account.accountCode.isNotBlank()) {
                    "${account.accountName} (${account.accountCode})"
                } else {
                    account.accountName.ifBlank { account.accountId }
                },
            amount = account.balance,
        )
    }

private fun emphasisRow(
    label: String,
    amount: Money,
): ReportRow = ReportRow(label = label, amount = amount, emphasis = true)

/**
 * Maps the [ConsolidatedReport] payload — as returned by
 * `ReportNetwork.consolidatedReportAsync` — into renderable sections. Only
 * fields present on the model are used: trial balance (per-account rows
 * netted as debit − credit, then the returned totals), P&L, balance sheet,
 * cash flow and the `eliminations` list (rendered separately by the caller).
 * An absent sub-report simply yields no section; [minorityInterest] is a
 * header-level figure, not a section.
 */
fun reportSections(report: ConsolidatedReport): List<ReportSection> =
    buildList {
        report.trialBalance?.let { tb ->
            // Trial-balance rows carry separate debit/credit sides; a section
            // row has one amount, so rows are netted (debit − credit) and the
            // untouched server totals close the section.
            val rows =
                tb.rows.map { row ->
                    ReportRow(
                        label =
                            if (row.accountCode.isNotBlank()) {
                                "${row.accountName} (${row.accountCode})"
                            } else {
                                row.accountName.ifBlank { row.accountId }
                            },
                        amount = row.debit - row.credit,
                    )
                } +
                    emphasisRow("Total debit", tb.totalDebit) +
                    emphasisRow("Total credit", tb.totalCredit)
            add(
                ReportSection(
                    title = if (tb.asOfDate.isNotBlank()) "Trial balance · as of ${tb.asOfDate}" else "Trial balance",
                    rows = rows,
                ),
            )
        }
        report.profitAndLoss?.let { pnl ->
            val rows =
                accountRows(pnl.revenueAccounts) +
                    emphasisRow("Total revenue", pnl.totalRevenue) +
                    accountRows(pnl.expenseAccounts) +
                    emphasisRow("Total expenses", pnl.totalExpenses) +
                    emphasisRow("Net profit", pnl.netProfit)
            add(ReportSection(title = "Profit & loss", rows = rows))
        }
        report.balanceSheet?.let { bs ->
            val rows =
                accountRows(bs.assets) +
                    emphasisRow("Total assets", bs.totalAssets) +
                    accountRows(bs.liabilities) +
                    emphasisRow("Total liabilities", bs.totalLiabilities) +
                    accountRows(bs.equity) +
                    emphasisRow("Total equity", bs.totalEquity)
            add(ReportSection(title = "Balance sheet", rows = rows))
        }
        report.cashFlow?.let { cf ->
            add(
                ReportSection(
                    title = "Cash flow · ${cf.method.ifBlank { "INDIRECT" }}",
                    rows =
                        listOf(
                            ReportRow("Opening cash", cf.openingCash),
                            ReportRow("Operating", cf.operatingCashFlow),
                            ReportRow("Investing", cf.investingCashFlow),
                            ReportRow("Financing", cf.financingCashFlow),
                            emphasisRow("Net cash flow", cf.netCashFlow),
                            emphasisRow("Closing cash", cf.closingCash),
                        ),
                ),
            )
        }
    }

/** Sum of elimination amounts — the section total under the eliminations table. */
fun eliminationTotal(entries: List<EliminationEntry>): Money = entries.fold(Money.ZERO) { acc, entry -> acc + entry.amount }

/**
 * Validation error for the new-elimination sheet, or null when submittable.
 * The debit/credit accounts must differ — a self-entry eliminates nothing.
 */
fun validateEliminationForm(
    debitAccountId: String,
    creditAccountId: String,
    amount: Money,
): String? =
    when {
        debitAccountId.isBlank() -> "Pick a debit account"
        creditAccountId.isBlank() -> "Pick a credit account"
        debitAccountId == creditAccountId -> "Debit and credit accounts must differ"
        amount <= Money.ZERO -> "Enter an amount above zero"
        else -> null
    }

/**
 * [CreateEliminationEntryRequest] has no linked-voucher/journal field, so the
 * reference is folded into the description (the sheet says so out loud).
 */
fun eliminationDescription(
    description: String,
    linkedVoucherId: String,
): String {
    val desc = description.trim()
    val link = linkedVoucherId.trim()
    return when {
        link.isBlank() -> desc
        desc.isBlank() -> "Linked voucher $link"
        else -> "$desc [linked voucher $link]"
    }
}
