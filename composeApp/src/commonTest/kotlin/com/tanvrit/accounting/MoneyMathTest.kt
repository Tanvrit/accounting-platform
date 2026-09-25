package com.tanvrit.accounting

import com.tanvrit.accounting.screens.common.formatMoney
import com.tanvrit.accounting.screens.common.parseMoneyInput
import com.tanvrit.accounting.screens.reconciliation.StatementCsvParser
import com.tanvrit.core.feature.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MoneyMathTest {
    @Test
    fun parseMoneyInputHandlesCommasAndBlanks() {
        assertEquals(Money.fromDouble(12500.50), parseMoneyInput("12,500.50"))
        assertEquals(Money.ZERO, parseMoneyInput(""))
        assertEquals(Money.ZERO, parseMoneyInput("not-a-number"))
        assertEquals(Money.fromDouble(-42.0), parseMoneyInput("-42"))
    }

    @Test
    fun formatMoneyRendersInrWithIndianGrouping() {
        assertEquals("₹1,25,000.50", formatMoney(Money.fromDouble(125000.50), "INR"))
        assertEquals("-₹42.00", formatMoney(Money.fromDouble(-42.0), "INR"))
    }

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
}
