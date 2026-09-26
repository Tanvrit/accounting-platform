package com.tanvrit.accounting.screens.importWizard

import com.tanvrit.core.feature.accounting.model.AccountType
import com.tanvrit.core.feature.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * AccountImportParser edge cases — delimiter sniffing, header detection,
 * column aliases, Tally group mapping, Indian-grouping amounts, duplicate
 * codes, per-row error text, and the negative-amount side flip.
 */
class AccountImportParserTest {
    // --- delimiter sniffing ---

    @Test
    fun csvDelimiterIsDetected() {
        val result = AccountImportParser.parseCsv("name,code\nCash,1001")
        assertEquals(',', result.delimiter)
        assertEquals("CSV", result.delimiterLabel)
    }

    @Test
    fun csvRowsParse() {
        val result = AccountImportParser.parseCsv("name,code\nCash,1001")
        assertEquals(1, result.rows.size)
        assertEquals("Cash", result.rows.single().name)
        assertEquals("1001", result.rows.single().code)
    }

    @Test
    fun tsvDelimiterIsDetected() {
        val result = AccountImportParser.parseCsv("name\tcode\topening\nCash\t1001\t500.00")
        assertEquals('\t', result.delimiter)
        assertEquals("TSV", result.delimiterLabel)
        assertEquals(Money.fromDouble(500.00), result.rows.single().opening)
    }

    @Test
    fun tsvWinsEvenWhenAmountsCarryGroupingCommas() {
        val result = AccountImportParser.parseCsv("name\tcode\topening\nCash\t1001\t1,50,000.00")
        assertEquals('\t', result.delimiter)
        assertEquals(Money.fromDouble(150000.00), result.rows.single().opening)
    }

    // --- header detection ---

    @Test
    fun headerRowIsDetectedAndCountsAsRowOne() {
        val result = AccountImportParser.parseCsv("name,code\nCash,1001")
        assertTrue(result.hasHeader)
        assertEquals(2, result.rows.single().rowNumber)
    }

    @Test
    fun headerlessRowsUsePositionalOrder() {
        val result = AccountImportParser.parseCsv("Cash,1001,ASSET,,500.00,DR,Petty cash")
        assertTrue(!result.hasHeader)
        val row = result.rows.single()
        assertEquals(1, row.rowNumber)
        assertEquals("Cash", row.name)
        assertEquals("1001", row.code)
        assertEquals(AccountType.ASSET, row.type)
        assertEquals(Money.fromDouble(500.00), row.opening)
        assertEquals(BalanceSide.DR, row.side)
        assertEquals("Petty cash", row.narration)
        assertTrue(row.isValid, "unexpected errors: ${row.errors}")
    }

    @Test
    fun headerIsCaseInsensitiveAndColumnsMayBeReordered() {
        val raw =
            """
            Code, Name, TYPE, Opening Balance
            1001, Cash, Asset, 250.00
            """.trimIndent()
        val row = AccountImportParser.parseCsv(raw).rows.single()
        assertTrue(row.isValid, "unexpected errors: ${row.errors}")
        assertEquals("Cash", row.name)
        assertEquals("1001", row.code)
        assertEquals(Money.fromDouble(250.00), row.opening)
    }

    // --- aliases ---

    @Test
    fun columnAliasesAreRecognised() {
        val raw =
            """
            Account Name, Account Code, Group, Parent Code, Balance, Dr/Cr, Description
            Steel Traders, 2001, Sundry Debtors, , 1500.00, DR, Customer
            """.trimIndent()
        val row = AccountImportParser.parseCsv(raw).rows.single()
        assertTrue(row.isValid, "unexpected errors: ${row.errors}")
        assertEquals("Steel Traders", row.name)
        assertEquals("2001", row.code)
        assertEquals(AccountType.ASSET, row.type)
        assertEquals(Money.fromDouble(1500.00), row.opening)
        assertEquals(BalanceSide.DR, row.side)
        assertEquals("Customer", row.narration)
    }

    @Test
    fun blankTypeDefaultsToAsset() {
        val result = AccountImportParser.parseCsv("name,code,type\nCash,1001,")
        val row = result.rows.single()
        assertEquals(AccountType.ASSET, row.type)
        assertTrue(row.isValid, "unexpected errors: ${row.errors}")
    }

    // --- type mapping ---

    @Test
    fun incomeAliasMapsToRevenue() {
        val row = AccountImportParser.parseCsv("name,code,type\nSales,4001,INCOME").rows.single()
        assertEquals(AccountType.REVENUE, row.type)
        assertTrue(row.isValid, "unexpected errors: ${row.errors}")
    }

    @Test
    fun tallyPrimaryGroupsMapToNearestPrimaryType() {
        val expectations =
            mapOf(
                "Sundry Debtors" to AccountType.ASSET,
                "Bank Accounts" to AccountType.ASSET,
                "Cash-in-Hand" to AccountType.ASSET,
                "Fixed Assets" to AccountType.ASSET,
                "Sundry Creditors" to AccountType.LIABILITY,
                "Duties & Taxes" to AccountType.LIABILITY,
                "Secured Loans" to AccountType.LIABILITY,
                "Capital Account" to AccountType.EQUITY,
                "Reserves & Surplus" to AccountType.EQUITY,
                "Sales Accounts" to AccountType.REVENUE,
                "Indirect Incomes" to AccountType.REVENUE,
                "Purchase Accounts" to AccountType.EXPENSE,
                "Indirect Expenses" to AccountType.EXPENSE,
            )
        expectations.forEach { (group, expected) ->
            val row = AccountImportParser.parseCsv("name,code,group\nX,1,$group").rows.single()
            assertEquals(expected, row.type, "Tally group \"$group\"")
            assertTrue(row.isValid, "Tally group \"$group\" row errors: ${row.errors}")
        }
    }

    @Test
    fun unknownTypeProducesRowErrorWithRawValue() {
        val raw =
            """
            name,code,type
            Cash,1001,ASSET
            Mystery,9999,Wealth Fund
            """.trimIndent()
        val result = AccountImportParser.parseCsv(raw)
        assertEquals(1, result.validRows.size)
        assertEquals(1, result.invalidCount)
        val bad = result.rows[1]
        assertEquals(3, bad.rowNumber)
        val error = bad.errors.first()
        assertTrue(error.contains("Row 3:"), "actual: $error")
        assertTrue(error.contains("type \"Wealth Fund\""), "actual: $error")
        assertTrue(error.contains("not recognised"), "actual: $error")
    }

    // --- amounts ---

    @Test
    fun indianGroupingAmountParsesFromQuotedCsvCell() {
        val raw =
            """
            name,code,type,opening
            Steel Traders,1001,ASSET,"1,50,000.00"
            """.trimIndent()
        val row = AccountImportParser.parseCsv(raw).rows.single()
        assertTrue(row.isValid, "unexpected errors: ${row.errors}")
        assertEquals(Money.fromDouble(150000.00), row.opening)
    }

    @Test
    fun invalidAmountIsARowErrorNotZero() {
        val row = AccountImportParser.parseCsv("name,code,opening\nCash,1001,abc").rows.single()
        assertTrue(row.errors.any { it.contains("opening balance \"abc\" is not a number") }, "actual: ${row.errors}")
        assertEquals(Money.ZERO, row.opening)
    }

    @Test
    fun blankOpeningIsZeroWithoutError() {
        val row = AccountImportParser.parseCsv("name,code,opening\nCash,1001,").rows.single()
        assertEquals(Money.ZERO, row.opening)
        assertTrue(row.isValid, "unexpected errors: ${row.errors}")
    }

    // --- duplicates / structural errors ---

    @Test
    fun duplicateAccountCodesFlagTheLaterRow() {
        val raw =
            """
            name,code
            Cash,1001
            Bank,1001
            """.trimIndent()
        val result = AccountImportParser.parseCsv(raw)
        assertTrue(result.rows[0].isValid, "unexpected errors: ${result.rows[0].errors}")
        assertTrue(
            result.rows[1].errors.any { it.contains("duplicate account code \"1001\"") && it.contains("row 2") },
            "actual: ${result.rows[1].errors}",
        )
    }

    @Test
    fun missingNameAndCodeArePerRowErrors() {
        val row = AccountImportParser.parseCsv("name,code\n,").rows.single()
        assertTrue(row.errors.any { it.contains("name is required") })
        assertTrue(row.errors.any { it.contains("account code is required") })
    }

    @Test
    fun accountCannotBeItsOwnParent() {
        val row = AccountImportParser.parseCsv("name,code,parent\nGroup,1001,1001").rows.single()
        assertTrue(row.errors.any { it.contains("own parent") }, "actual: ${row.errors}")
    }

    // --- side / negative flip ---

    @Test
    fun negativeAmountWithExplicitSideFlipsToCr() {
        val row = AccountImportParser.parseCsv("name,code,type,opening,side\nCreditor,2001,LIABILITY,-500.00,DR").rows.single()
        assertEquals(BalanceSide.CR, row.side)
        assertEquals(Money.fromDouble(-500.00), row.opening)
        assertTrue(row.isValid, "unexpected errors: ${row.errors}")
    }

    @Test
    fun negativeAmountWithoutSideCarriesTheSign() {
        val row = AccountImportParser.parseCsv("name,code,opening\nCreditor,2001,-500.00").rows.single()
        assertNull(row.side)
        assertEquals(Money.fromDouble(-500.00), row.opening)
    }

    @Test
    fun crSideNegatesAPositiveAmount() {
        val row = AccountImportParser.parseCsv("name,code,opening,side\nCapital,3001,1000.00,CR").rows.single()
        assertEquals(Money.fromDouble(-1000.00), row.opening)
        assertEquals(BalanceSide.CR, row.side)
    }

    @Test
    fun badSideWordIsARowError() {
        val row = AccountImportParser.parseCsv("name,code,opening,side\nCash,1001,10,credit-side").rows.single()
        assertTrue(row.errors.any { it.contains("side \"credit-side\" not recognised") }, "actual: ${row.errors}")
    }

    // --- line endings ---

    @Test
    fun crlfLineEndingsParseCleanly() {
        val raw =
            "name,code,type,opening,side\r\n" +
                "Cash,1001,ASSET,100.00,DR\r\n" +
                "Creditor,2001,LIABILITY,200.00,CR\r\n"
        val result = AccountImportParser.parseCsv(raw)
        assertTrue(result.hasHeader)
        assertEquals(2, result.rows.size)
        assertTrue(result.rows.all { it.isValid }, "errors: ${result.rows.flatMap { it.errors }}")
        assertEquals(Money.fromDouble(100.00), result.rows[0].opening)
        assertEquals(Money.fromDouble(-200.00), result.rows[1].opening)
    }

    @Test
    fun emptyPasteYieldsNoRows() {
        val result = AccountImportParser.parseCsv("")
        assertTrue(result.rows.isEmpty())
        assertTrue(!result.hasHeader)
    }
}
