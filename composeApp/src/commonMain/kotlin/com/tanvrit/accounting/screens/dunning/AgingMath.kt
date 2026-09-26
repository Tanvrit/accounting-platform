package com.tanvrit.accounting.screens.dunning

import com.tanvrit.core.feature.accounting.model.Account
import com.tanvrit.core.feature.accounting.model.Voucher
import com.tanvrit.core.feature.accounting.model.VoucherLineItem
import com.tanvrit.core.feature.accounting.model.VoucherStatus
import com.tanvrit.core.feature.accounting.model.VoucherType
import com.tanvrit.core.feature.money.Money
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/** Receivables aging buckets — days PAST DUE (invoice date + payment terms). */
enum class AgingBucket(
    val label: String,
) {
    DAYS_0_30("0–30"),
    DAYS_31_60("31–60"),
    DAYS_61_90("61–90"),
    DAYS_91_PLUS("91+"),
}

/** One unpaid sale invoice — a POSTED `SALE` voucher, at FULL value (see [AgingMath]). */
data class AgedInvoice(
    val voucherId: String,
    val voucherNumber: String,
    /** Invoice date, ISO yyyy-mm-dd. */
    val date: String,
    /** [date] + terms days; blank when the invoice date did not parse. */
    val dueDate: String,
    /** Whole days past [dueDate], clamped at 0 (0 = current / not yet due). */
    val daysOverdue: Int,
    val bucket: AgingBucket,
    /** Full receivable-side amount of the voucher — payments are NOT netted. */
    val amount: Money,
    val partyAccountId: String,
)

/** Per-party aggregation: every open invoice of one customer account. */
data class PartyAging(
    val partyAccountId: String,
    /** Resolved from the account cache; falls back to the line's accountName/code/id. */
    val name: String,
    val accountCode: String,
    val gstin: String = "",
    val email: String = "",
    val phone: String = "",
    /** Ascending by invoice date (ISO strings compare lexically). */
    val invoices: List<AgedInvoice>,
) {
    val total: Money get() = invoices.fold(Money.ZERO) { acc, invoice -> acc + invoice.amount }
    val invoiceCount: Int get() = invoices.size
    val lastInvoiceDate: String get() = invoices.maxOfOrNull { it.date } ?: ""

    /** Oldest (largest) days-overdue across the party's invoices — drives the suggested tone. */
    val maxDaysOverdue: Int get() = invoices.maxOfOrNull { it.daysOverdue } ?: 0

    fun bucketAmount(bucket: AgingBucket): Money =
        invoices.filter { it.bucket == bucket }.fold(Money.ZERO) { acc, invoice -> acc + invoice.amount }

    fun bucketCount(bucket: AgingBucket): Int = invoices.count { it.bucket == bucket }
}

/** Count + total of one bucket across all parties (the summary strip). */
data class BucketSummary(
    val bucket: AgingBucket,
    val count: Int,
    val total: Money,
)

/**
 * Pure receivables-aging math — no I/O, so `commonTest` covers it directly
 * (`commonTest/.../screens/dunning/AgingMathTest.kt`).
 *
 * CONVENTIONS (documented honestly, none of them faked):
 * - Receivable = a DEBIT leg of a POSTED [VoucherType.SALE] voucher (a sale
 *   debits the customer/receivable side, credits revenue/GST). DRAFT vouchers
 *   never post; CANCELLED/REVERSED carry no balance.
 * - Legs hitting an account (or carrying a line name/code) containing
 *   "cash"/"bank" are NOT receivables — that mirrors the cash/bank vocabulary
 *   `VoucherEntryViewModel.smartDefaults` uses, and keeps counter sales out of
 *   the dunning list.
 * - Outstanding = the FULL sale amount, marked OPEN. Payment/receipt
 *   allocation is not available client-side — nothing here pretends to net
 *   payments, credit notes or reversals (the screen surfaces that footnote).
 * - Terms: days tolerated before an invoice starts aging, read from
 *   `voucher.dimensions["termsDays"|"paymentTermsDays"]`, then the same keys
 *   on the party `Account`, then [DEFAULT_TERMS_DAYS]. Neither model carries
 *   a first-class terms field, so dimensions are the honest carrier.
 * - An invoice dated AFTER the reference date clamps to 0 days overdue (it is
 *   current), never negative; an unparseable date ages as 0 and renders its
 *   due date blank, never silently re-dated.
 */
object AgingMath {
    const val DEFAULT_TERMS_DAYS = 30

    private const val TERMS_KEY_PRIMARY = "termsDays"
    private const val TERMS_KEY_ALT = "paymentTermsDays"
    private const val MAX_TERMS_DAYS = 3650

    private val TERMS_KEYS = listOf(TERMS_KEY_PRIMARY, TERMS_KEY_ALT)

    private val BUCKET_ORDER = AgingBucket.entries.toList()

    /** Payment terms in days for [voucher], honouring voucher→account→default resolution. */
    fun termsDaysOf(
        voucher: Voucher,
        account: Account?,
    ): Int {
        val fromVoucher = termsFrom(voucher.dimensions)
        if (fromVoucher != null) return fromVoucher
        val fromAccount = account?.dimensions?.let(::termsFrom)
        if (fromAccount != null) return fromAccount
        return DEFAULT_TERMS_DAYS
    }

    /** First parseable terms value under the well-known keys; null when absent/garbage. */
    private fun termsFrom(dimensions: Map<String, String>): Int? {
        for (key in TERMS_KEYS) {
            val parsed = dimensions[key]?.trim()?.toIntOrNull()
            if (parsed != null) return parsed.coerceIn(0, MAX_TERMS_DAYS)
        }
        return null
    }

    /** Due date = invoice date + [termsDays]; null when the invoice date is not ISO. */
    fun dueDateOf(
        invoiceDateIso: String,
        termsDays: Int,
    ): String? = parseIso(invoiceDateIso)?.plus(DatePeriod(days = termsDays))?.toString()

    /**
     * Whole days the invoice is past due at [referenceDateIso] — `reference −
     * (invoiceDate + termsDays)`, clamped at 0. Null when either date is not
     * ISO yyyy-mm-dd (callers decide the fallback; [agingOf] uses 0).
     */
    fun daysOverdue(
        invoiceDateIso: String,
        termsDays: Int,
        referenceDateIso: String,
    ): Int? {
        val invoiceDate = parseIso(invoiceDateIso) ?: return null
        val reference = parseIso(referenceDateIso) ?: return null
        val due = invoiceDate.plus(DatePeriod(days = termsDays))
        return due.daysUntil(reference).coerceAtLeast(0)
    }

    /** Bucket boundaries: 0–30 / 31–60 / 61–90 / 91+ days past due. */
    fun bucketOf(daysOverdue: Int): AgingBucket =
        when {
            daysOverdue <= 30 -> AgingBucket.DAYS_0_30
            daysOverdue <= 60 -> AgingBucket.DAYS_31_60
            daysOverdue <= 90 -> AgingBucket.DAYS_61_90
            else -> AgingBucket.DAYS_91_PLUS
        }

    /**
     * The aging entry point the ViewModel calls: classify every open
     * receivable invoice of [vouchers] against [accountById] (the cached
     * account map) and aggregate per party. Parties at zero or CREDIT total
     * are excluded (no debt to dun), parties sort by total descending.
     *
     * A voucher debiting SEVERAL customer accounts produces one row per party
     * at that party's debit total; a voucher with no non-cash/bank debit leg
     * contributes nothing (counter sale).
     */
    fun agingOf(
        vouchers: List<Voucher>,
        accountById: Map<String, Account>,
        referenceDate: String,
    ): List<PartyAging> {
        val invoices =
            vouchers
                .filter { it.status == VoucherStatus.POSTED && it.voucherType == VoucherType.SALE }
                .flatMap { voucher -> receivableInvoicesOf(voucher, accountById, referenceDate) }

        return invoices
            .groupBy { it.partyAccountId }
            .map { (partyId, partyInvoices) ->
                val account = accountById[partyId]
                PartyAging(
                    partyAccountId = partyId,
                    name = partyNameOf(partyId, account, partyInvoices),
                    accountCode = account?.accountCode ?: "",
                    gstin = contactValue(account, GSTIN_KEYS),
                    email = contactValue(account, EMAIL_KEYS),
                    phone = contactValue(account, PHONE_KEYS),
                    invoices = partyInvoices.sortedWith(compareBy({ it.date }, { it.voucherNumber })),
                )
            }.filter { it.total.isPositive } // zero/credit-balance parties have nothing to dun
            .sortedByDescending { it.total }
    }

    /** Receivable-side open invoices of ONE voucher (may be empty for counter sales). */
    private fun receivableInvoicesOf(
        voucher: Voucher,
        accountById: Map<String, Account>,
        referenceDate: String,
    ): List<AgedInvoice> =
        voucher.lineItems
            .filter { it.debit.isPositive }
            .filter { it.accountId.isNotBlank() }
            .filter { !isCashLike(accountById[it.accountId], it) }
            .groupBy { it.accountId }
            .map { (accountId, legs) ->
                val account = accountById[accountId]
                val termsDays = termsDaysOf(voucher, account)
                val overdue = daysOverdue(voucher.date, termsDays, referenceDate) ?: 0
                AgedInvoice(
                    voucherId = voucher.id,
                    voucherNumber = voucher.voucherNumber,
                    date = voucher.date,
                    dueDate = dueDateOf(voucher.date, termsDays) ?: "",
                    daysOverdue = overdue,
                    bucket = bucketOf(overdue),
                    amount = legs.fold(Money.ZERO) { acc, leg -> acc + leg.debit },
                    partyAccountId = accountId,
                )
            }

    /**
     * True when the leg is a cash/bank leg rather than a customer receivable.
     * Uses the account from the cache when present, else the line's denormalized
     * name/code — both fall under the same "cash"/"bank" name vocabulary the
     * voucher-entry smart defaults use.
     */
    fun isCashLike(
        account: Account?,
        line: VoucherLineItem,
    ): Boolean {
        val candidates =
            listOfNotNull(
                account?.name,
                account?.accountCode,
                line.accountName,
                line.accountCode,
            )
        return candidates.any { name ->
            CASH_WORDS.any { word -> name.contains(word, ignoreCase = true) }
        }
    }

    /** Summary strip content, in bucket order, for an already-computed party list. */
    fun summaries(parties: List<PartyAging>): List<BucketSummary> =
        BUCKET_ORDER.map { bucket ->
            BucketSummary(
                bucket = bucket,
                count = parties.sumOf { it.bucketCount(bucket) },
                total = parties.fold(Money.ZERO) { acc, party -> acc + party.bucketAmount(bucket) },
            )
        }

    /** Search / min-amount / bucket filtering over the computed party list. */
    fun applyFilters(
        parties: List<PartyAging>,
        minAmount: Money,
        bucket: AgingBucket?,
        query: String,
    ): List<PartyAging> {
        val needle = query.trim().lowercase()
        return parties
            .filter { it.total >= minAmount }
            .filter { bucket == null || it.bucketCount(bucket) > 0 }
            .filter {
                needle.isEmpty() ||
                    it.name.lowercase().contains(needle) ||
                    it.accountCode.lowercase().contains(needle)
            }
    }

    private fun partyNameOf(
        partyId: String,
        account: Account?,
        invoices: List<AgedInvoice>,
    ): String =
        account?.name?.ifBlank { null }
            ?: account?.accountCode?.ifBlank { null }
            ?: partyId

    /** Reads well-known contact keys off the account — never the whole map (PII discipline). */
    private fun contactValue(
        account: Account?,
        keys: List<String>,
    ): String {
        if (account == null) return ""
        for (key in keys) {
            account.dimensions[key]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { return it }
            account.taxConfig
                ?.metadata
                ?.get(key)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { return it }
        }
        return ""
    }

    private fun parseIso(dateIso: String): LocalDate? = runCatching { LocalDate.parse(dateIso.trim()) }.getOrNull()

    private val CASH_WORDS = listOf("cash", "bank")
    private val GSTIN_KEYS = listOf("gstin", "gstNumber")
    private val EMAIL_KEYS = listOf("email", "emailAddress")
    private val PHONE_KEYS = listOf("phone", "mobile")
}
