package com.tanvrit.accounting.screens.itcWorkspace

import com.tanvrit.core.feature.accounting.model.Account
import com.tanvrit.core.feature.accounting.model.Voucher
import com.tanvrit.core.feature.accounting.model.VoucherStatus
import com.tanvrit.core.feature.accounting.model.VoucherType

/** Match result for one 2B row (or books-only voucher). */
data class ItcMatchRow(
    val key: String,
    val bucket: ItcBucket,
    val row: ItcRow?,
    val voucher: Voucher?,
    val supplierLabel: String,
    val twoBTaxMinorUnits: Long,
    val booksTaxMinorUnits: Long,
    val diffMinorUnits: Long,
)

enum class ItcBucket(
    val code: String,
    val label: String,
) {
    MATCHED("MATCHED", "Matched"),
    AMOUNT_MISMATCH("AMOUNT_MISMATCH", "Amount mismatch"),
    MISSING_IN_BOOKS("MISSING_IN_BOOKS", "Missing in books"),
    DUPLICATE_IN_BOOKS("DUPLICATE_IN_BOOKS", "Duplicate in books"),
    BOOKS_ONLY("BOOKS_ONLY", "In books, not in 2B"),
}

/**
 * 2B ↔ purchase-register matcher (roadmap #8) — pure, client-side.
 *
 * Key: supplier GSTIN + normalized invoice number when the party's account
 * carries a GSTIN (`Account.dimensions["gstin"]` / `["gstNumber"]` —
 * defensively; absent → amount+invoice-number matching with the on-screen
 * caption stating GSTIN cross-check was skipped for those rows).
 *
 * A voucher-row amount comparison is tax-tolerant to ± ₹1 (per-side) because
 * supplier rounding conventions differ.
 */
object ItcMatcher {
    const val TAX_TOLERANCE_MINOR_UNITS = 100L // ₹1

    fun normalizeInvoiceNumber(raw: String): String = raw.trim().uppercase().replace(Regex("[\\s\\-/_]"), "")

    /** GSTIN alphanumeric sanity: 15 chars, 2-digit state + PAN-ish mid + checks — shape only, NOT checksum. */
    fun gstinWellFormed(gstin: String): Boolean = gstin.trim().matches(Regex("^[0-9]{2}[A-Z0-9]{13}$"))

    fun match(
        rows: List<ItcRow>,
        purchaseVouchers: List<Voucher>,
        accountsById: Map<String, Account>,
    ): List<ItcMatchRow> {
        val out = mutableListOf<ItcMatchRow>()

        val booksByKey = LinkedHashMap<String, MutableList<Voucher>>()
        for (voucher in purchaseVouchers) {
            if (voucher.status != VoucherStatus.POSTED || voucher.voucherType != VoucherType.PURCHASE) continue
            val invoiceNumber = voucher.referenceId.ifBlank { voucher.voucherNumber }
            val key = keyFor(booksGstinOf(voucher, accountsById), invoiceNumber)
            booksByKey.getOrPut(key) { mutableListOf() } += voucher
        }

        val consumed = mutableSetOf<String>()
        for (row in rows) {
            val key = keyFor(row.supplierGstin, row.invoiceNumber)
            val candidates = booksByKey[key].orEmpty()
            val rowTax = row.itcMinorUnits
            when {
                candidates.isEmpty() -> {
                    out +=
                        ItcMatchRow(
                            key,
                            ItcBucket.MISSING_IN_BOOKS,
                            row,
                            null,
                            supplierLabelFor(row, null, accountsById),
                            rowTax,
                            0L,
                            rowTax,
                        )
                }
                else -> {
                    val toleranceMatch =
                        candidates.firstOrNull { v ->
                            kotlin.math.abs(booksTaxOf(v) - rowTax) <= TAX_TOLERANCE_MINOR_UNITS
                        }
                    when {
                        toleranceMatch != null -> {
                            consumed += voucherKey(toleranceMatch)
                            out +=
                                ItcMatchRow(
                                    key,
                                    ItcBucket.MATCHED,
                                    row,
                                    toleranceMatch,
                                    supplierLabelFor(row, toleranceMatch, accountsById),
                                    rowTax,
                                    booksTaxOf(toleranceMatch),
                                    0L,
                                )
                        }
                        else -> {
                            val head = candidates.first()
                            consumed += voucherKey(head)
                            val booksTax = booksTaxOf(head)
                            out +=
                                ItcMatchRow(
                                    key,
                                    ItcBucket.AMOUNT_MISMATCH,
                                    row,
                                    head,
                                    supplierLabelFor(row, head, accountsById),
                                    rowTax,
                                    booksTax,
                                    rowTax - booksTax,
                                )
                        }
                    }
                    if (candidates.size > 1) {
                        candidates.drop(1).forEach { dup ->
                            consumed += voucherKey(dup)
                            out +=
                                ItcMatchRow(
                                    key,
                                    ItcBucket.DUPLICATE_IN_BOOKS,
                                    row,
                                    dup,
                                    supplierLabelFor(row, dup, accountsById),
                                    rowTax,
                                    booksTaxOf(dup),
                                    rowTax - booksTaxOf(dup),
                                )
                        }
                    }
                }
            }
        }

        // Books-only: posted purchase vouchers never consumed by any 2B row.
        for ((_, vouchers) in booksByKey) {
            for (voucher in vouchers) {
                if (voucherKey(voucher) in consumed) continue
                out +=
                    ItcMatchRow(
                        keyFor(booksGstinOf(voucher, accountsById), voucher.referenceId.ifBlank { voucher.voucherNumber }),
                        ItcBucket.BOOKS_ONLY,
                        null,
                        voucher,
                        supplierLabelFor(null, voucher, accountsById),
                        0L,
                        booksTaxOf(voucher),
                        -booksTaxOf(voucher),
                    )
            }
        }
        return out.sortedWith(compareBy({ it.bucket.ordinal }, { it.supplierLabel }))
    }

    fun summarize(rows: List<ItcMatchRow>): Map<ItcBucket, Pair<Int, Long>> =
        rows.groupBy { it.bucket }.mapValues { (_, list) ->
            list.size to list.sumOf { it.twoBTaxMinorUnits }
        }

    /** ITC the books carry for the voucher: sum of GST-tagged credit legs' amounts. */
    private fun booksTaxOf(voucher: Voucher): Long =
        voucher.lineItems
            .filter { isTaxAccount(it.accountCode, it.accountName) }
            .sumOf { (it.debit + it.credit).absoluteMinor() }

    private fun isTaxAccount(
        code: String,
        name: String,
    ): Boolean {
        val probe = "$code $name".lowercase()
        return probe.contains("gst") || probe.contains("igst") || probe.contains("cgst") || probe.contains("sgst")
    }

    private fun booksGstinOf(
        voucher: Voucher,
        accountsById: Map<String, Account>,
    ): String {
        val counterparty = counterpartyAccountOf(voucher, accountsById) ?: return ""
        val dims = counterparty.dimensions
        return (
            dims["gstin"] ?: dims["gstNumber"] ?: dims["gstinNumber"] ?: ""
        ).trim().uppercase()
    }

    /** The non-cash/bank credit-side leg = the vendor (purchases credit the supplier). */
    private fun counterpartyAccountOf(
        voucher: Voucher,
        accountsById: Map<String, Account>,
    ): Account? {
        val creditLeg =
            voucher.lineItems.firstOrNull { leg ->
                val name = leg.accountName.lowercase()
                !name.contains("cash") && !name.contains("bank") && leg.credit.amountInSmallestUnit > 0L
            } ?: voucher.lineItems.firstOrNull()
        return creditLeg?.let { accountsById[it.accountId] }
    }

    private fun supplierLabelFor(
        row: ItcRow?,
        voucher: Voucher?,
        accountsById: Map<String, Account>,
    ): String {
        val party = voucher?.let { counterpartyAccountOf(it, accountsById) }
        return (
            party?.name?.takeIf { it.isNotBlank() }
                ?: row?.supplierGstin?.takeIf { it.isNotBlank() }
                ?: party?.accountCode?.takeIf { it.isNotBlank() }
                ?: "Unknown supplier"
        )
    }

    private fun keyFor(
        gstin: String,
        invoiceNumber: String,
    ): String = "${gstin.trim().uppercase()}|${normalizeInvoiceNumber(invoiceNumber)}"

    private fun voucherKey(voucher: Voucher): String = voucher.id.ifBlank { "${voucher.voucherNumber}:${voucher.date}" }
}

private fun com.tanvrit.core.feature.money.Money.absoluteMinor(): Long = kotlin.math.abs(amountInSmallestUnit)
