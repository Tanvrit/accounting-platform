package com.tanvrit.accounting.screens.multiCurrency

import kotlin.math.abs
import kotlin.math.round

/**
 * Pure formatting/parsing helpers for FX rates and minor-unit money on the
 * multi-currency screen. Common-source-set safe: no `String.format` (not
 * available in Kotlin common), no new dependencies.
 *
 * Rate convention (matching the SDK model `FxRate.rate: Double`): a rate is a
 * plain decimal — "how many counter-currency units one base-currency unit
 * buys", e.g. base INR, counter USD, rate 0.0120.
 */
object FxFormat {
    /** Sanity bound for [parseRate]: no real FX pair vs INR exceeds 1e9. */
    private const val MAX_RATE = 1.0e9

    /**
     * Fraction digits finer than this are rejected by [convertMinorUnits]
     * (12 digits keeps the 10^k denominator comfortably inside Long range).
     */
    private const val MAX_RATE_DECIMALS = 12

    /** 4dp scaling used by [formatRate]. */
    private const val RATE_SCALE = 10_000L

    /**
     * Display form of a rate string: 4 decimal places, trailing zeros trimmed
     * but never below 2 — "1.23456789" → "1.2346", "0.5" → "0.50",
     * "82" → "82.00", "82.3500" → "82.35".
     *
     * Rounds half-up at the 4th decimal via [round] on a scaled Long, which is
     * exact for any rate representable to ~15 significant digits. Magnitudes
     * whose 1e4-scaled value would leave Long's exact range (≥ 9e13) come back
     * as the trimmed input — display must never fabricate a number.
     * Unparseable input (incl. NaN/∞) is returned trimmed, unchanged.
     */
    fun formatRate(rate: String): String {
        val cleaned = rate.trim().replace(",", "")
        val value = cleaned.toDoubleOrNull() ?: return cleaned
        if (value.isNaN() || value.isInfinite()) return cleaned
        val magnitude = abs(value)
        if (magnitude >= 9.0e13) return cleaned
        val scaled = round(magnitude * RATE_SCALE).toLong()
        val whole = scaled / RATE_SCALE
        var frac = (scaled % RATE_SCALE).toString().padStart(4, '0')
        while (frac.length > 2 && frac.endsWith('0')) frac = frac.dropLast(1)
        return (if (value < 0) "-" else "") + "$whole.$frac"
    }

    /**
     * Validates a user-typed rate and returns the cleaned string (trimmed,
     * commas stripped — " 82,350.0 " → "82350.0"), or null when invalid:
     * blank, non-numeric garbage, NaN/∞, ≤ 0, or above [MAX_RATE].
     *
     * Scientific notation ("1e3") parses fine here — it is a valid number —
     * but is rejected by [convertMinorUnits], which needs digit-exact input.
     */
    fun parseRate(raw: String): String? {
        val cleaned = raw.trim().replace(",", "")
        if (cleaned.isEmpty()) return null
        val value = cleaned.toDoubleOrNull() ?: return null
        if (value.isNaN() || value.isInfinite()) return null
        if (value <= 0.0 || value > MAX_RATE) return null
        return cleaned
    }

    /**
     * Converts minor units by a decimal rate string using **integer math
     * only** — [rate] is split into whole/fraction digit groups and applied as
     * `(amount * digits) / 10^fracLen`; no floating point anywhere.
     *
     * Returns null when:
     * - [rate] fails [parseRate] (zero/negative/NaN/garbage/>1e9 included), or
     *   is not plain digits (scientific notation rejected — cannot be
     *   digit-split honestly), or
     * - the fraction needs > [MAX_RATE_DECIMALS] digits, or
     * - the intermediate product would overflow Long (null, never a wrapped
     *   result).
     *
     * The final division **truncates toward zero**: a sub-minor-unit remainder
     * is dropped — 1 minor unit × rate "1.5" = 1.5 → 1, and -1 × "1.5" → -1.
     * Rounding policy for real postings lives server-side in the revaluation
     * run, not in this display/preview helper.
     */
    fun convertMinorUnits(
        amountMinorUnits: Long,
        rate: String,
    ): Long? {
        val cleaned = parseRate(rate) ?: return null
        val dot = cleaned.indexOf('.')
        val wholeDigits = if (dot < 0) cleaned else cleaned.substring(0, dot)
        val fracDigits = if (dot < 0) "" else cleaned.substring(dot + 1).trimEnd('0')
        if (wholeDigits.any { !it.isDigit() } || fracDigits.any { !it.isDigit() }) return null
        if (fracDigits.length > MAX_RATE_DECIMALS) return null
        val numerator = (wholeDigits + fracDigits).toLongOrNull() ?: return null
        var denominator = 1L
        repeat(fracDigits.length) { denominator *= 10L }
        // Wrapped-product detection: if (a*b mod 2^64) divides back to exactly
        // b with zero remainder, the true product fitted in a Long.
        val scaled = amountMinorUnits * numerator
        if (amountMinorUnits != 0L && (scaled % amountMinorUnits != 0L || scaled / amountMinorUnits != numerator)) {
            return null
        }
        return scaled / denominator
    }

    /**
     * Plain "major.minor" rendering of minor units at the codebase's ISO-2
     * default scale (same convention as `Money.toString`: 12345 → "123.45",
     * 7 → "0.07", -250 → "-2.50").
     *
     * Deliberately currency-blind — zero/three-decimal currencies render
     * through `MoneyText`/`formatMoney` with their real ISO exponent; this is
     * for captions and preview maths only.
     */
    fun minorToMajor(minor: Long): String {
        val negative = minor < 0
        val magnitude = if (negative) -minor else minor
        val whole = magnitude / 100
        val frac = (magnitude % 100).toString().padStart(2, '0')
        return (if (negative) "-" else "") + "$whole.$frac"
    }
}
