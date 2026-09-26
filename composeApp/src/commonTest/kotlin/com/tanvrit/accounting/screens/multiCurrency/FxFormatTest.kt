package com.tanvrit.accounting.screens.multiCurrency

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Edge cases for [FxFormat] — the pure rate/money helpers driving the
 * multi-currency screen. Truncation behaviour of [FxFormat.convertMinorUnits]
 * is pinned deliberately: it always truncates toward zero and never rounds a
 * sub-minor-unit remainder up.
 */
class FxFormatTest {
    // --- formatRate ---

    @Test
    fun formatRateRoundsToFourDpAndTrimsTrailingZeros() {
        assertEquals("1.2346", FxFormat.formatRate("1.23456789"))
        assertEquals("82.35", FxFormat.formatRate("82.3500"))
        assertEquals("0.012", FxFormat.formatRate("0.0120")) // 4dp, trailing zero trimmed, ≥2dp kept
    }

    @Test
    fun formatRateKeepsAtLeastTwoDp() {
        assertEquals("0.50", FxFormat.formatRate("0.5"))
        assertEquals("82.00", FxFormat.formatRate("82"))
    }

    @Test
    fun formatRatePassesUnparseableInputThroughUnchanged() {
        assertEquals("abc", FxFormat.formatRate("abc"))
        assertEquals("", FxFormat.formatRate(""))
        assertEquals("NaN", FxFormat.formatRate("NaN"))
    }

    // --- parseRate ---

    @Test
    fun parseRateRejectsZeroNegativeNaNGarbageAndHugeRates() {
        assertNull(FxFormat.parseRate("0"))
        assertNull(FxFormat.parseRate("0.0"))
        assertNull(FxFormat.parseRate("-1"))
        assertNull(FxFormat.parseRate("NaN"))
        assertNull(FxFormat.parseRate("abc"))
        assertNull(FxFormat.parseRate(""))
        assertNull(FxFormat.parseRate("1e10")) // > 1e9 sanity bound
    }

    @Test
    fun parseRateStripsCommasAndWhitespace() {
        assertEquals("82350.0", FxFormat.parseRate(" 82,350.0 "))
        assertEquals("0.0001", FxFormat.parseRate("0.0001"))
        assertEquals("1234.5", FxFormat.parseRate("1,234.5"))
    }

    // --- convertMinorUnits ---

    @Test
    fun convertMinorUnitsReturnsNullForZeroOrUnparseableRates() {
        assertNull(FxFormat.convertMinorUnits(100, "0"))
        assertNull(FxFormat.convertMinorUnits(100, "0.000"))
        assertNull(FxFormat.convertMinorUnits(100, "abc"))
        // Scientific notation parses as a number but is not digit-exact — rejected.
        assertNull(FxFormat.convertMinorUnits(100, "1e3"))
    }

    @Test
    fun convertMinorUnitsIsExactWithIntegerMath() {
        assertEquals(8_235L, FxFormat.convertMinorUnits(100, "82.35"))
        // JPY-scale counter rates (>1000 per base unit) stay exact.
        assertEquals(150_325L, FxFormat.convertMinorUnits(100, "1503.25"))
        assertEquals(0L, FxFormat.convertMinorUnits(0, "82.35"))
        assertEquals(123_456L, FxFormat.convertMinorUnits(10_000, "12.3456"))
    }

    @Test
    fun convertMinorUnitsTruncatesSubMinorRemaindersTowardZero() {
        // 1 minor unit × 1.5 = 1.5 minor units — the .5 is dropped, never rounded up.
        assertEquals(1L, FxFormat.convertMinorUnits(1, "1.5"))
        // Toward zero, not toward -∞: -1.5 → -1.
        assertEquals(-1L, FxFormat.convertMinorUnits(-1, "1.5"))
    }

    @Test
    fun convertMinorUnitsReturnsNullOnLongOverflowInsteadOfWrapping() {
        assertNull(FxFormat.convertMinorUnits(Long.MAX_VALUE, "2"))
    }

    // --- minorToMajor ---

    @Test
    fun minorToMajorRendersIsoTwoScalePlainDecimals() {
        assertEquals("123.45", FxFormat.minorToMajor(12_345))
        assertEquals("1.00", FxFormat.minorToMajor(100))
        assertEquals("0.07", FxFormat.minorToMajor(7))
        assertEquals("-2.50", FxFormat.minorToMajor(-250))
        assertEquals("0.00", FxFormat.minorToMajor(0))
    }
}
