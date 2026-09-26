package com.tanvrit.accounting.screens.ledger

import com.tanvrit.core.feature.accounting.model.Voucher
import com.tanvrit.core.feature.accounting.model.VoucherStatus
import com.tanvrit.core.feature.money.Money

/**
 * One posting against the ledger's account — a view over a single
 * `VoucherLineItem` of a voucher, with just enough voucher header context to
 * render a ledger row and to group back to the full voucher.
 */
data class LedgerEntry(
    /** Composite, stable across reloads (voucher id + line id). */
    val entryId: String,
    val voucherId: String,
    val voucherNumber: String,
    val voucherType: String,
    val date: String,
    val narration: String,
    val debit: Money = Money.ZERO,
    val credit: Money = Money.ZERO,
)

/** A ledger row: the entry plus the account balance immediately after it. */
data class LedgerLine(
    val entry: LedgerEntry,
    /** Running balance, debit-positive (positive = net Dr, negative = net Cr). */
    val runningBalance: Money,
)

/**
 * Pure ledger math — no I/O, so `commonTest` covers it directly
 * (`commonTest/.../LedgerMathTest.kt`).
 *
 * Sign convention matches the server's trial-balance
 * (`server/.../report/ReportServiceImpl`): a posting contributes
 * `debit − credit`, and only `VoucherStatus.POSTED` vouchers affect balances
 * (DRAFT never posts, CANCELLED/REVERSED carry no balance — a reversal lands
 * as its own POSTED voucher of type REVERSAL).
 */
object LedgerMath {
    /** Returns the postings of [accountId] from [vouchers] — POSTED vouchers only. */
    fun entriesForAccount(
        vouchers: List<Voucher>,
        accountId: String,
    ): List<LedgerEntry> =
        vouchers
            .filter { it.status == VoucherStatus.POSTED }
            .flatMap { voucher ->
                voucher.lineItems
                    .filter { it.accountId == accountId }
                    .map { line ->
                        LedgerEntry(
                            entryId = "${voucher.id}:${line.id}",
                            voucherId = voucher.id,
                            voucherNumber = voucher.voucherNumber,
                            voucherType = voucher.voucherType.code,
                            date = voucher.date,
                            narration = line.narration.ifBlank { voucher.narration },
                            debit = line.debit,
                            credit = line.credit,
                        )
                    }
            }

    /** Signed delta of one posting — debit-positive. */
    fun signedDelta(entry: LedgerEntry): Money = entry.debit - entry.credit

    /**
     * Ascending date order (ISO yyyy-mm-dd dates compare lexically), with
     * same-day rows ordered by voucher number then entry id so the running
     * balance is deterministic for a fixed data set.
     */
    fun sortAscending(entries: List<LedgerEntry>): List<LedgerEntry> =
        // Stable sort (Kotlin sortedWith is stable): ties on date+voucherNumber keep
        // the input order of the voucher's legs — deterministic regardless of the
        // (possibly random) ids assigned to entries, at both runtime and in tests.
        entries.sortedWith(compareBy({ it.date }, { it.voucherNumber }))

    /**
     * Balance at the start of [fromDate]: the account's opening balance plus
     * every posted delta dated strictly before it (the "carry" from prior
     * periods). A blank [fromDate] means "no carry" — the opening is the
     * account's opening balance.
     */
    fun openingAt(
        openingBalance: Money,
        entries: List<LedgerEntry>,
        fromDate: String,
    ): Money {
        if (fromDate.isBlank()) return openingBalance
        return entries
            .filter { it.date < fromDate }
            .fold(openingBalance) { acc, entry -> acc + signedDelta(entry) }
    }

    /**
     * Entries in [fromDate, toDate] (inclusive; ISO lexicographic), ascending,
     * each carrying the running balance starting from [opening]. Blank bounds
     * are open-ended.
     */
    fun linesInRange(
        entries: List<LedgerEntry>,
        fromDate: String,
        toDate: String,
        opening: Money,
    ): List<LedgerLine> {
        var balance = opening
        return sortAscending(entries.filter { inRange(it.date, fromDate, toDate) })
            .map { entry ->
                balance += signedDelta(entry)
                LedgerLine(entry = entry, runningBalance = balance)
            }
    }

    /** Closing balance of a computed ledger — the last running balance, or [opening] when empty. */
    fun closingOf(
        opening: Money,
        lines: List<LedgerLine>,
    ): Money = lines.lastOrNull()?.runningBalance ?: opening

    /** Indian FY start (April 1) for an ISO date — default "from" for ledger windows. */
    fun fiscalYearStart(todayIso: String): String {
        val year = todayIso.substringBefore("-").toIntOrNull() ?: return todayIso
        val month = todayIso.substringAfter("-").substringBefore("-").toIntOrNull() ?: 4
        return (if (month >= 4) year else year - 1).toString().padStart(4, '0') + "-04-01"
    }

    private fun inRange(
        date: String,
        fromDate: String,
        toDate: String,
    ): Boolean = (fromDate.isBlank() || date >= fromDate) && (toDate.isBlank() || date <= toDate)
}

/**
 * "1,25,000.00 Dr" / "…Cr" style: the unsigned balance plus the Dr/Cr side.
 * Positive or zero balances are Dr, negative are Cr (debit-positive convention).
 */
fun Money.toDrCr(): Pair<Money, String> = if (isNegative) -this to "Cr" else this to "Dr"
