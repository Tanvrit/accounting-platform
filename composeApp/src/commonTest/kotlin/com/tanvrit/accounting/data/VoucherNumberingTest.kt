package com.tanvrit.accounting.data

import com.tanvrit.core.feature.accounting.model.VoucherType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Voucher numbering series (#10): formatting, padding, increment semantics and
 * the legacy-prefix defaults. Pure logic only — `NumberingSeriesStore` resolves
 * `UserDefaults` through TanvritKoin at property init and is deliberately not
 * constructed in commonTest (see AccountingAppModuleTest for why); the store's
 * `peekNumber` / `consumeNumber` / `updateSeries` all delegate to the
 * [VoucherNumbering] functions asserted here.
 */
class VoucherNumberingTest {
    @Test
    fun rendersPrefixPaddedNumberAndSuffix() {
        val series = NumberingSeries(prefix = "JV", nextNumber = 7, width = 4, suffix = "-FY26")
        assertEquals("JV0007-FY26", VoucherNumbering.format(series))
    }

    @Test
    fun numberLongerThanWidthIsNotTruncated() {
        // padStart grows but never cuts: a 6-digit number at width 4 stays whole.
        val series = NumberingSeries(prefix = "INV", nextNumber = 123456, width = 4)
        assertEquals("INV123456", VoucherNumbering.format(series))
    }

    @Test
    fun blankPrefixAndSuffixAreAllowed() {
        assertEquals("0001", VoucherNumbering.format(NumberingSeries()))
    }

    @Test
    fun widthIsClampedIntoBoundsOnSanitize() {
        val tooSmall = VoucherNumbering.sanitized(NumberingSeries(prefix = "INV", nextNumber = 1, width = 0))
        assertEquals(VoucherNumbering.MIN_WIDTH, tooSmall.width)
        assertEquals("INV1", VoucherNumbering.format(tooSmall))
        assertEquals(
            VoucherNumbering.MAX_WIDTH,
            VoucherNumbering.sanitized(NumberingSeries(width = 99)).width,
        )
    }

    @Test
    fun nextNumberBelowOneIsClampedOnSanitize() {
        assertEquals(1, VoucherNumbering.sanitized(NumberingSeries(nextNumber = 0)).nextNumber)
        assertEquals(1, VoucherNumbering.sanitized(NumberingSeries(nextNumber = -5)).nextNumber)
    }

    @Test
    fun incrementAdvancesNumberKeepingAffixes() {
        val series = NumberingSeries(prefix = "RCPT", nextNumber = 41, width = 5, suffix = "/A")
        val next = VoucherNumbering.incremented(series)
        assertEquals(NumberingSeries(prefix = "RCPT", nextNumber = 42, width = 5, suffix = "/A"), next)
        assertEquals("RCPT00041/A", VoucherNumbering.format(series))
        assertEquals("RCPT00042/A", VoucherNumbering.format(next))
    }

    @Test
    fun consumeSequenceCountsUpWithoutReuse() {
        // The exact shape of NumberingSeriesStore.consumeNumber: format, then advance.
        var series = NumberingSeries(prefix = "PMT", nextNumber = 8, width = 3)
        val issued = mutableListOf<String>()
        repeat(3) {
            issued.add(VoucherNumbering.format(series))
            series = VoucherNumbering.incremented(series)
        }
        assertEquals(listOf("PMT008", "PMT009", "PMT010"), issued)
    }

    @Test
    fun defaultsAreSeededFromLegacyPrefixSettings() {
        val settings = AccountingSettings(salesPrefix = "INV", receiptPrefix = "RC")
        assertEquals("INV", VoucherNumbering.defaultFor(VoucherType.SALE, settings).prefix)
        assertEquals("RC", VoucherNumbering.defaultFor(VoucherType.RECEIPT, settings).prefix)
    }

    @Test
    fun typesWithoutLegacyPrefixDefaultToTheirCode() {
        assertEquals("CONTRA", VoucherNumbering.defaultFor(VoucherType.CONTRA).prefix)
        assertEquals("CREDIT_NOTE", VoucherNumbering.defaultFor(VoucherType.CREDIT_NOTE).prefix)
    }

    @Test
    fun editableTypesMatchTheComposerTabsExactlyOnce() {
        assertEquals(
            listOf(
                VoucherType.SALE,
                VoucherType.PURCHASE,
                VoucherType.RECEIPT,
                VoucherType.PAYMENT,
                VoucherType.JOURNAL,
                VoucherType.CONTRA,
            ),
            VoucherNumbering.editableTypes,
        )
    }
}
