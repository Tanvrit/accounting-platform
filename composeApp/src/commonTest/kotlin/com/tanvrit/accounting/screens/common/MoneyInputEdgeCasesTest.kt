package com.tanvrit.accounting.screens.common

import com.tanvrit.core.feature.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Edge cases for `parseMoneyInput` / `formatMoney` / `twoDecimals` /
 * `signedPercent` in AccountingComponents.kt. Where the current behaviour is
 * a limit rather than a feature (parenthesized negatives, embedded currency
 * symbols), the test pins the behaviour as-is so a future parser change has to
 * update the pin deliberately.
 */
class MoneyInputEdgeCasesTest {
    // --- parseMoneyInput: comma handling ---

    @Test
    fun parseMoneyInputToleratesIndianGrouping() {
        assertEquals(Money.fromDouble(123456.78), parseMoneyInput("1,23,456.78"))
        assertEquals(Money.fromDouble(1000000.0), parseMoneyInput("10,00,000"))
        // Commas are stripped blindly, regardless of position validity.
        assertEquals(Money.fromDouble(123.4), parseMoneyInput("1,2,3.4"))
    }

    // --- parseMoneyInput: fallback paths (invalid input is never an error) ---

    @Test
    fun parseMoneyInputFallsBackToZeroOnUnparsableInput() {
        assertEquals(Money.ZERO, parseMoneyInput(""))
        assertEquals(Money.ZERO, parseMoneyInput("   "))
        assertEquals(Money.ZERO, parseMoneyInput("-"))
        assertEquals(Money.ZERO, parseMoneyInput("1.2.3"))
        assertEquals(Money.ZERO, parseMoneyInput("1 250"))
        // Parenthesized negatives "(42.50)" from exported statements are NOT
        // supported: the whole token fails toDoubleOrNull and reads as zero.
        assertEquals(Money.ZERO, parseMoneyInput("(42.50)"))
        // Currency symbols are not stripped.
        assertEquals(Money.ZERO, parseMoneyInput("₹1,250.00"))
    }

    @Test
    fun parseMoneyInputTrimsOuterWhitespaceAndKeepsSign() {
        assertEquals(Money.fromDouble(42.5), parseMoneyInput("  42.5  "))
        assertEquals(Money.fromDouble(-42.5), parseMoneyInput("-42.5"))
    }

    @Test
    fun parseMoneyInputAbsorbsBinaryFloatDriftIntoMinorUnits() {
        // The classic 0.1 + 0.2 != 0.3 trap: fromDouble rounds to minor units.
        assertEquals(
            parseMoneyInput("0.30"),
            parseMoneyInput("0.10") + parseMoneyInput("0.20"),
        )
    }

    // --- formatMoney ---

    @Test
    fun formatMoneyRendersLakhCroreGroupingForInr() {
        assertEquals("₹1,23,456.78", formatMoney(Money.fromDouble(123456.78), "INR"))
        assertEquals("₹10,00,000.00", formatMoney(Money.fromDouble(1000000.0), "INR"))
        assertEquals("₹0.00", formatMoney(Money.ZERO, "INR"))
        assertEquals("-₹1,234.50", formatMoney(Money.fromDouble(-1234.50), "INR"))
    }

    @Test
    fun formatMoneyFollowsCurrencyExponentAndGrouping() {
        // USD: western grouping, two decimals.
        assertEquals("$1,234,567.89", formatMoney(Money.fromDouble(1234567.89), "USD"))
        // JPY: zero fraction digits — the display path is ISO-exponent aware.
        assertEquals("¥1,000", formatMoney(Money.fromSmallestUnit(1000), "JPY"))
    }

    // --- twoDecimals / signedPercent ---

    @Test
    fun twoDecimalsRoundsAndCarries() {
        assertEquals("1.00", twoDecimals(1.0))
        assertEquals("0.10", twoDecimals(0.1))
        assertEquals("-3.46", twoDecimals(-3.456))
        assertEquals("13.00", twoDecimals(12.999))
    }

    @Test
    fun signedPercentAddsSignAndDropsSecondDecimal() {
        assertEquals("+4.2%", signedPercent(4.25))
        assertEquals("-1.0%", signedPercent(-1.04))
        assertEquals("+0.0%", signedPercent(0.0))
        // Pinned as-is: magnitudes below 0.1% render without their minus sign
        // (the "-0.0" whole-part survives as "0").
        assertEquals("0.0%", signedPercent(-0.04))
    }
}
