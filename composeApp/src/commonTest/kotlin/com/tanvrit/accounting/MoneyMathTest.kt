package com.tanvrit.accounting

import com.tanvrit.accounting.screens.common.formatMoney
import com.tanvrit.accounting.screens.common.parseMoneyInput
import com.tanvrit.core.feature.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
