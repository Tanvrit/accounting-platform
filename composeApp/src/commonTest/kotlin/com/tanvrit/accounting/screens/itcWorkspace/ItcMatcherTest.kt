package com.tanvrit.accounting.screens.itcWorkspace

import com.tanvrit.core.feature.accounting.model.Account
import com.tanvrit.core.feature.accounting.model.AccountType
import com.tanvrit.core.feature.accounting.model.Voucher
import com.tanvrit.core.feature.accounting.model.VoucherLineItem
import com.tanvrit.core.feature.accounting.model.VoucherStatus
import com.tanvrit.core.feature.accounting.model.VoucherType
import com.tanvrit.core.feature.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals

class ItcMatcherTest {
    private fun supplierAccount(gstin: String = ""): Account =
        Account(
            businessId = "biz",
            accountCode = "SUP1",
            name = "Supplier One",
            type = AccountType.LIABILITY,
            dimensions = if (gstin.isBlank()) emptyMap() else mapOf("gstin" to gstin),
        )

    private fun purchaseVoucher(
        id: String,
        referenceInvoice: String,
        taxMinor: Long,
        gstinAccount: Account,
    ): Voucher =
        Voucher(
            businessId = "biz",
            voucherNumber = "P-$id",
            voucherType = VoucherType.PURCHASE,
            date = "2026-04-10",
            narration = "",
            referenceId = referenceInvoice,
            status = VoucherStatus.POSTED,
            lineItems =
                listOf(
                    VoucherLineItem(
                        accountId = "acc-exp",
                        accountCode = "5000",
                        accountName = "Purchases",
                        debit = Money.fromDouble(1000.0),
                        credit = Money.ZERO,
                    ),
                    VoucherLineItem(
                        accountId = "acc-gst",
                        accountCode = "1199",
                        accountName = "GST Input",
                        debit = Money.fromDouble(taxMinor / 100.0),
                        credit = Money.ZERO,
                    ),
                    VoucherLineItem(
                        accountId = gstinAccount.id,
                        accountCode = gstinAccount.accountCode,
                        accountName = gstinAccount.name,
                        debit = Money.ZERO,
                        credit = Money.fromDouble(1000.0 + taxMinor / 100.0),
                    ),
                ),
        )

    @Test
    fun `matched rows pair by gstin+invoice and diff is zero`() {
        val supplier = supplierAccount("27ABCDE1234F1Z5")
        val voucher = purchaseVoucher("v1", "INV-22", 180_000, supplier)
        val rows =
            listOf(
                ItcRow(
                    1,
                    supplierGstin = "27ABCDE1234F1Z5",
                    invoiceNumber = "inv-22",
                    invoiceDate = "2026-04-10",
                    igstMinorUnits = 180_000L,
                ),
            )
        val matched = ItcMatcher.match(rows, listOf(voucher), mapOf(supplier.id to supplier))
        assertEquals(1, matched.size)
        assertEquals(ItcBucket.MATCHED, matched.first().bucket)
        assertEquals(0L, matched.first().diffMinorUnits)
    }

    @Test
    fun `invoice-number normalization strips spaces-dashes-slashes`() {
        assertEquals("INV22B", ItcMatcher.normalizeInvoiceNumber(" inv-22/B "))
    }

    @Test
    fun `amount mismatch beyond tolerance is flagged, inside it matches`() {
        val supplier = supplierAccount("27ABCDE1234F1Z5")
        val inTol = purchaseVoucher("v1", "INV-22", 180_000, supplier)
        val rows =
            listOf(
                ItcRow(1, supplierGstin = "27ABCDE1234F1Z5", invoiceNumber = "INV-22", igstMinorUnits = 180_000L + 99L),
            )
        assertEquals(ItcBucket.MATCHED, ItcMatcher.match(rows, listOf(inTol), mapOf(supplier.id to supplier)).first().bucket)

        val beyond = rows.first().copy(igstMinorUnits = 180_000L + 101L)
        val matched = ItcMatcher.match(listOf(beyond), listOf(inTol), mapOf(supplier.id to supplier)).first()
        assertEquals(ItcBucket.AMOUNT_MISMATCH, matched.bucket)
        assertEquals(101L, matched.diffMinorUnits)
    }

    @Test
    fun `missing-in-books and duplicate-in-books are distinct buckets`() {
        val supplier = supplierAccount("27ABCDE1234F1Z5")
        val dup1 = purchaseVoucher("v1", "INV-22", 180_000, supplier)
        val dup2 = purchaseVoucher("v2", "INV-22", 180_000, supplier)
        val rows = listOf(ItcRow(1, supplierGstin = "27ABCDE1234F1Z5", invoiceNumber = "INV-22", igstMinorUnits = 180_000L))
        val matched = ItcMatcher.match(rows, listOf(dup1, dup2), mapOf(supplier.id to supplier))
        assertEquals(setOf(ItcBucket.MATCHED, ItcBucket.DUPLICATE_IN_BOOKS), matched.map { it.bucket }.toSet())

        val missing = ItcMatcher.match(rows, emptyList(), emptyMap()).first()
        assertEquals(ItcBucket.MISSING_IN_BOOKS, missing.bucket)
        assertEquals("27ABCDE1234F1Z5", missing.supplierLabel)
    }

    @Test
    fun `books-only purchases surface when no 2B row consumes them`() {
        val supplier = supplierAccount("27ABCDE1234F1Z5")
        val voucher = purchaseVoucher("v9", "INV-99", 90_000, supplier)
        val matched = ItcMatcher.match(emptyList(), listOf(voucher), mapOf(supplier.id to supplier))
        assertEquals(1, matched.size)
        assertEquals(ItcBucket.BOOKS_ONLY, matched.first().bucket)
        assertEquals(90_000L, matched.first().booksTaxMinorUnits)
    }

    @Test
    fun `summarize totals per bucket`() {
        val supplier = supplierAccount("27ABCDE1234F1Z5")
        val v1 = purchaseVoucher("v1", "INV-1", 100_000, supplier)
        val v2 = purchaseVoucher("v2", "INV-2", 200_000, supplier)
        val rows =
            listOf(
                ItcRow(1, supplierGstin = "27ABCDE1234F1Z5", invoiceNumber = "INV-1", igstMinorUnits = 100_000L),
                ItcRow(2, supplierGstin = "27ABCDE1234F1Z5", invoiceNumber = "INV-MISSING", igstMinorUnits = 50_000L),
            )
        val matched = ItcMatcher.match(rows, listOf(v1, v2), mapOf(supplier.id to supplier))
        val summary = ItcMatcher.summarize(matched)
        assertEquals(1, summary[ItcBucket.MATCHED]?.first)
        assertEquals(100_000L, summary[ItcBucket.MATCHED]?.second)
        assertEquals(1, summary[ItcBucket.MISSING_IN_BOOKS]?.first)
        assertEquals(1, summary[ItcBucket.BOOKS_ONLY]?.first)
    }
}
