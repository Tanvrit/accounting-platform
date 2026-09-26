package com.tanvrit.accounting.screens.recurring

import kotlinx.datetime.LocalDate

/** How often a recurring schedule fires. Server wire uses [code] strings. */
enum class RecurrenceFrequency(
    val code: String,
    val label: String,
) {
    DAILY("DAILY", "Daily"),
    WEEKLY("WEEKLY", "Weekly"),
    MONTHLY("MONTHLY", "Monthly"),
    QUARTERLY("QUARTERLY", "Quarterly"),
    YEARLY("YEARLY", "Yearly"),
    ;

    companion object {
        fun fromCodeOrDefault(code: String): RecurrenceFrequency = entries.firstOrNull { it.code == code } ?: MONTHLY
    }
}

/**
 * Pure recurrence math for recurring vouchers (roadmap #2). Month/Quarter/Year
 * steps clamp at month end (Jan 31 + MONTHLY → Feb 28, leap-aware), matching
 * what Tally/Zoho users expect a "31st monthly" rule to do.
 */
object RecurringScheduleMath {
    /** The due date strictly after [from] for [frequency]. */
    fun nextDueFrom(
        frequency: RecurrenceFrequency,
        from: LocalDate,
    ): LocalDate =
        when (frequency) {
            RecurrenceFrequency.DAILY -> LocalDate.fromEpochDays(from.toEpochDays() + 1)
            RecurrenceFrequency.WEEKLY -> LocalDate.fromEpochDays(from.toEpochDays() + 7)
            RecurrenceFrequency.MONTHLY -> plusMonthsClamped(from, 1)
            RecurrenceFrequency.QUARTERLY -> plusMonthsClamped(from, 3)
            RecurrenceFrequency.YEARLY -> plusMonthsClamped(from, 12)
        }

    /**
     * Every due occurrence in `(nextDue .. today]` — used by the engine so a
     * schedule that missed N periods while the app was closed drafts N vouchers.
     * End-dated schedules stop after the end date; disabled schedules produce none.
     */
    fun dueOccurrences(
        frequency: RecurrenceFrequency,
        nextDue: LocalDate,
        today: LocalDate,
        endDate: LocalDate?,
        enabled: Boolean,
    ): List<LocalDate> {
        if (!enabled) return emptyList()
        val out = mutableListOf<LocalDate>()
        var cursor = nextDue
        // Safety cap: a DAILY schedule left untouched for 5 years would otherwise
        // try to draft ~1.8k vouchers in one go — stop at 366 (one full leap year).
        while (out.size < MAX_OCCURRENCES_PER_RUN && cursor <= today && (endDate == null || cursor <= endDate)) {
            out += cursor
            cursor = nextDueFrom(frequency, cursor)
        }
        return out
    }

    fun isDue(
        frequency: RecurrenceFrequency,
        nextDue: LocalDate,
        today: LocalDate,
        endDate: LocalDate?,
        enabled: Boolean,
    ): Boolean = dueOccurrences(frequency, nextDue, today, endDate, enabled).isNotEmpty()

    const val MAX_OCCURRENCES_PER_RUN = 366

    // ---- month math (kotlinx-datetime has no localized clamp for LocalDate) ----

    private fun plusMonthsClamped(
        from: LocalDate,
        months: Int,
    ): LocalDate {
        // month.ordinal is 0-based (JANUARY=0) — works on every kotlinx-datetime line.
        val totalMonths = from.year * MONTHS_PER_YEAR + from.month.ordinal + months
        val year = totalMonths / MONTHS_PER_YEAR
        val monthNumber = totalMonths % MONTHS_PER_YEAR + 1
        val day = minOf(from.day, daysIn(year, monthNumber))
        return LocalDate(year, monthNumber, day)
    }

    private fun daysIn(
        year: Int,
        monthNumber: Int,
    ): Int =
        when (monthNumber) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (isLeap(year)) 29 else 28
            else -> error("Invalid monthNumber: $monthNumber")
        }

    private fun isLeap(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

    private const val MONTHS_PER_YEAR = 12
}
