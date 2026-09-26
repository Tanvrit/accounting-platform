package com.tanvrit.accounting.screens.reconciliation

import com.tanvrit.core.feature.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * StatementCsvParser edge cases. Several tests pin *current* behaviour of the
 * naive `split(",")` parser (no RFC-4180 quoting, shape-only date regex,
 * any-header-token header detection) so a future parser upgrade has to update
 * the pins deliberately.
 */
class CsvParserEdgeCasesTest {
    // --- moved from MoneyMathTest ---

    @Test
    fun csvParserReadsHeaderlessRows() {
        val raw =
            """
            2025-04-05, NEFT RENT APR, -45000.00, 312500.00, NEFT123, DEBIT
            2025-04-07, UPI SALES, 60000.00, 372500.00, UPI456, CREDIT
            """.trimIndent()
        val (statement, errors) = StatementCsvParser.toStatement("acc-1", raw)
        assertTrue(errors.isEmpty(), "unexpected parse errors: $errors")
        assertEquals(2, statement?.transactions?.size)
        assertEquals("372500.00".let { Money.fromDouble(372500.00) }, statement?.closingBalance)
    }

    @Test
    fun csvParserCollectsBadRowsWithoutDroppingGoodOnes() {
        val raw =
            """
            date,narration,amount,balance,reference,type
            2025-04-05, NEFT RENT APR, -45000.00, 312500.00, NEFT123, DEBIT
            04/05/2025, bad date, 10.00, 10.00, X, CREDIT
            2025-04-07, UPI SALES, 60000.00, 372500.00, UPI456, CREDIT
            """.trimIndent()
        val result = StatementCsvParser.parse("acc-1", raw)
        assertEquals(2, result.transactions.size)
        assertEquals(1, result.errors.size)
    }

    // --- line endings / empty input ---

    @Test
    fun crlfLineEndingsParseCleanly() {
        val raw =
            "date,narration,amount,balance,reference,type\r\n" +
                "2025-04-05, NEFT, -45.00, 3125.00, N1, DEBIT\r\n" +
                "2025-04-07, UPI, 60.00, 3725.00, U2, CREDIT\r\n"
        val result = StatementCsvParser.parse("acc-1", raw)
        assertTrue(result.errors.isEmpty(), "unexpected parse errors: ${result.errors}")
        assertEquals(2, result.transactions.size)
    }

    @Test
    fun emptyStatementYieldsErrorAndNoStatement() {
        val result = StatementCsvParser.parse("acc-1", "")
        assertEquals(listOf("Statement is empty"), result.errors)
        assertTrue(result.transactions.isEmpty())

        val (statement, toStatementErrors) = StatementCsvParser.toStatement("acc-1", "")
        assertNull(statement)
        assertEquals(result.errors, toStatementErrors)
    }

    // --- dates ---

    @Test
    fun nonIsoDatesAreRejectedWithRowNumbers() {
        val raw =
            """
            date,narration,amount,balance,reference,type
            05/04/2025, DD/MM/YYYY, 10.00, 10.00, X, CREDIT
            2025-4-5, short ISO, 20.00, 30.00, X, CREDIT
            2025-04-07, UPI SALES, 60.00, 90.00, U2, CREDIT
            """.trimIndent()
        val result = StatementCsvParser.parse("acc-1", raw)
        assertEquals(1, result.transactions.size)
        assertEquals(2, result.errors.size)
        assertTrue(result.errors[0].contains("Row 2"))
        assertTrue(result.errors[1].contains("Row 3"))
    }

    @Test
    fun dateRegexChecksShapeNotCalendarRange() {
        // Pinned as-is: month 13 / day 45 pass the \d{4}-\d{2}-\d{2} shape and
        // are imported verbatim — range validation is the server's job.
        val raw =
            """
            date,narration,amount,balance,reference,type
            2025-13-45, IMPOSSIBLE DATE, 10.00, 10.00, X, CREDIT
            """.trimIndent()
        val result = StatementCsvParser.parse("acc-1", raw)
        assertTrue(result.errors.isEmpty(), "unexpected parse errors: ${result.errors}")
        assertEquals("2025-13-45", result.transactions.single().date)
    }

    // --- amount sign / type column ---

    @Test
    fun debitTypeWordNegatesAPositiveAmount() {
        val raw =
            """
            date,narration,amount,balance,reference,type
            2025-04-05, NEFT RENT APR, 45000.00, 312500.00, NEFT123, DEBIT
            """.trimIndent()
        val result = StatementCsvParser.parse("acc-1", raw)
        val txn = result.transactions.single()
        assertEquals("DEBIT", txn.type)
        assertEquals(Money.fromDouble(45000.00), txn.amount)
    }

    @Test
    fun negativeAmountWinsOverCreditTypeWord() {
        val raw =
            """
            date,narration,amount,balance,reference,type
            2025-04-06, REVERSAL, -100.00, 10.00, R9, CREDIT
            """.trimIndent()
        val txn = StatementCsvParser.parse("acc-1", raw).transactions.single()
        assertEquals("DEBIT", txn.type)
        assertEquals(Money.fromDouble(100.00), txn.amount)
    }

    @Test
    fun unknownTypeWordWithPositiveAmountIsCredit() {
        val raw =
            """
            date,narration,amount,balance,reference,type
            2025-04-07, INTEREST, 5.25, 105.25, I1, XX
            """.trimIndent()
        val txn = StatementCsvParser.parse("acc-1", raw).transactions.single()
        assertEquals("CREDIT", txn.type)
        assertEquals(Money.fromDouble(5.25), txn.amount)
    }

    // --- header handling ---

    @Test
    fun headerIsCaseInsensitiveAndColumnsMayBeReordered() {
        val raw =
            """
            Narration, Amount, TYPE, Date, Balance, Reference
            UPI SALES, 60000.00, CREDIT, 2025-04-07, 372500.00, UPI456
            """.trimIndent()
        val result = StatementCsvParser.parse("acc-1", raw)
        assertTrue(result.errors.isEmpty(), "unexpected parse errors: ${result.errors}")
        val txn = result.transactions.single()
        assertEquals("2025-04-07", txn.date)
        assertEquals("UPI SALES", txn.narration)
        assertEquals(Money.fromDouble(60000.00), txn.amount)
        assertEquals("UPI456", txn.reference)
    }

    @Test
    fun firstRowCellMatchingAHeaderTokenHijacksHeaderDetection() {
        // Pinned as-is: header detection fires when ANY first-row cell equals a
        // known header word. A data row whose narration is literally "type"
        // turns the whole statement into a header and zero transactions parse.
        val raw =
            """
            2025-04-05, type, 10.00, 10.00, R1, DEBIT
            2025-04-06, UPI SALES, 60.00, 70.00, R2, CREDIT
            """.trimIndent()
        val result = StatementCsvParser.parse("acc-1", raw)
        assertTrue(result.transactions.isEmpty())
        assertEquals(1, result.errors.size)
        assertTrue(result.errors.single().contains("Row 2"))
    }

    @Test
    fun quotedCommaInNarrationBreaksTheRowButKeepsSiblings() {
        // Pinned as-is: the parser is a naive split(",") — RFC-4180 quoting is
        // not recognised, so the extra comma shifts columns and the row fails
        // with "not a number" while well-formed rows still import.
        val raw =
            """
            date,narration,amount,balance,reference,type
            2025-04-05, "NEFT, RENT APR", -45000.00, 312500.00, NEFT123, DEBIT
            2025-04-07, UPI SALES, 60000.00, 372500.00, UPI456, CREDIT
            """.trimIndent()
        val result = StatementCsvParser.parse("acc-1", raw)
        assertEquals(1, result.transactions.size)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors.single().contains("Row 2"))
        assertTrue(result.errors.single().contains("is not a number"))
    }

    @Test
    fun missingBalanceColumnDefaultsToZero() {
        val raw =
            """
            date,narration,amount,type
            2025-04-05, NEFT, -45.00, DEBIT
            """.trimIndent()
        val result = StatementCsvParser.parse("acc-1", raw)
        val txn = result.transactions.single()
        assertEquals(Money.ZERO, txn.balance)
    }

    // --- toStatement assembly ---

    @Test
    fun toStatementSortsByDateAndTakesClosingBalanceFromLatestRow() {
        // Rows deliberately out of order.
        val raw =
            """
            2025-04-07, UPI SALES, 60000.00, 372500.00, UPI456, CREDIT
            2025-04-05, NEFT RENT APR, -45000.00, 312500.00, NEFT123, DEBIT
            """.trimIndent()
        val (statement, errors) = StatementCsvParser.toStatement("acc-9", raw)
        assertTrue(errors.isEmpty(), "unexpected parse errors: $errors")
        requireNotNull(statement)
        assertEquals("acc-9", statement.bankAccountId)
        assertEquals("2025-04-05", statement.periodStart)
        assertEquals("2025-04-07", statement.periodEnd)
        assertEquals(Money.ZERO, statement.openingBalance)
        assertEquals(Money.fromDouble(372500.00), statement.closingBalance)
        assertEquals(listOf("2025-04-05", "2025-04-07"), statement.transactions.map { it.date })
    }
}
