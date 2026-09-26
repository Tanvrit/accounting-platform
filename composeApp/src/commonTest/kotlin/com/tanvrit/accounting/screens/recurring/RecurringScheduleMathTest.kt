package com.tanvrit.accounting.screens.recurring

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecurringScheduleMathTest {
    @Test
    fun `monthly clamp lands Dec-Jan31 to Feb 28 on non-leap year`() {
        val due = RecurringScheduleMath.nextDueFrom(RecurrenceFrequency.MONTHLY, LocalDate.parse("2025-01-31"))
        assertEquals(LocalDate.parse("2025-02-28"), due)
    }

    @Test
    fun `monthly clamp lands on Feb 29 in a leap year`() {
        val due = RecurringScheduleMath.nextDueFrom(RecurrenceFrequency.MONTHLY, LocalDate.parse("2024-01-31"))
        assertEquals(LocalDate.parse("2024-02-29"), due)
    }

    @Test
    fun `quarterly from mid-month keeps the day`() {
        val due = RecurringScheduleMath.nextDueFrom(RecurrenceFrequency.QUARTERLY, LocalDate.parse("2026-02-15"))
        assertEquals(LocalDate.parse("2026-05-15"), due)
    }

    @Test
    fun `yearly clamps leap day`() {
        val due = RecurringScheduleMath.nextDueFrom(RecurrenceFrequency.YEARLY, LocalDate.parse("2024-02-29"))
        assertEquals(LocalDate.parse("2025-02-28"), due)
    }

    @Test
    fun `daily and weekly roll across month boundaries`() {
        assertEquals(
            LocalDate.parse("2026-05-01"),
            RecurringScheduleMath.nextDueFrom(RecurrenceFrequency.DAILY, LocalDate.parse("2026-04-30")),
        )
        assertEquals(
            LocalDate.parse("2026-05-03"),
            RecurringScheduleMath.nextDueFrom(RecurrenceFrequency.WEEKLY, LocalDate.parse("2026-04-26")),
        )
    }

    @Test
    fun `dueOccurrences enumerates missed periods up to today`() {
        val due =
            RecurringScheduleMath.dueOccurrences(
                RecurrenceFrequency.MONTHLY,
                nextDue = LocalDate.parse("2026-04-30"),
                today = LocalDate.parse("2026-08-15"),
                endDate = null,
                enabled = true,
            )
        // Apr 30, May 30, Jun 30, Jul 30 — Aug 30 NOT yet due.
        assertEquals(listOf("2026-04-30", "2026-05-30", "2026-06-30", "2026-07-30"), due.map { it.toString() })
    }

    @Test
    fun `dueOccurrences respects end date and disabled`() {
        val capped =
            RecurringScheduleMath.dueOccurrences(
                RecurrenceFrequency.MONTHLY,
                nextDue = LocalDate.parse("2026-04-30"),
                today = LocalDate.parse("2026-12-31"),
                endDate = LocalDate.parse("2026-06-15"),
                enabled = true,
            )
        assertEquals(listOf("2026-04-30", "2026-05-30"), capped.map { it.toString() })
        assertTrue(
            RecurringScheduleMath
                .dueOccurrences(
                    RecurrenceFrequency.MONTHLY,
                    LocalDate.parse("2026-04-30"),
                    LocalDate.parse("2026-12-31"),
                    null,
                    enabled = false,
                ).isEmpty(),
        )
    }

    @Test
    fun `run-away daily schedule is capped at MAX_OCCURRENCES_PER_RUN`() {
        val due =
            RecurringScheduleMath.dueOccurrences(
                RecurrenceFrequency.DAILY,
                nextDue = LocalDate.parse("2020-01-01"),
                today = LocalDate.parse("2026-01-01"),
                endDate = null,
                enabled = true,
            )
        assertEquals(RecurringScheduleMath.MAX_OCCURRENCES_PER_RUN, due.size)
    }

    @Test
    fun `isDue false for future and disabled schedules`() {
        assertFalse(
            RecurringScheduleMath.isDue(
                RecurrenceFrequency.MONTHLY,
                LocalDate.parse("2026-12-01"),
                LocalDate.parse("2026-11-01"),
                null,
                enabled = true,
            ),
        )
        assertFalse(
            RecurringScheduleMath.isDue(
                RecurrenceFrequency.MONTHLY,
                LocalDate.parse("2026-10-01"),
                LocalDate.parse("2026-11-01"),
                null,
                enabled = false,
            ),
        )
    }
}
