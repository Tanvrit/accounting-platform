package com.tanvrit.accounting.screens.dunning

import com.tanvrit.accounting.screens.common.formatMoney
import com.tanvrit.core.feature.accounting.model.Account
import com.tanvrit.core.feature.accounting.model.AccountingTaxConfig
import com.tanvrit.core.feature.accounting.model.Voucher
import com.tanvrit.core.feature.accounting.model.VoucherLineItem
import com.tanvrit.core.feature.accounting.model.VoucherStatus
import com.tanvrit.core.feature.accounting.model.VoucherType
import com.tanvrit.core.feature.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ReminderLetterTest {
    private fun money(rupees: Long): Money = Money.fromSmallestUnit(rupees * 100)

    private fun party(
        accountId: String = "cust-1",
        name: String = "Acme Traders",
        code: String = "2001",
        gstin: String = "27AABCA1234Z1Z5",
        email: String = "",
        phone: String = "",
        invoices: List<AgedInvoice> = emptyList(),
    ): PartyAging =
        PartyAging(
            partyAccountId = accountId,
            name = name,
            accountCode = code,
            gstin = gstin,
            email = email,
            phone = phone,
            invoices = invoices,
        )

    private fun invoice(
        voucherId: String,
        number: String,
        date: String,
        amount: Money,
        daysOverdue: Int = 45,
    ): AgedInvoice =
        AgedInvoice(
            voucherId = voucherId,
            voucherNumber = number,
            date = date,
            dueDate = "2025-08-14",
            daysOverdue = daysOverdue,
            bucket = AgingMath.bucketOf(daysOverdue),
            amount = amount,
            partyAccountId = "cust-1",
        )

    private fun input(party: PartyAging) =
        LetterInput(
            businessName = "Tanvrit Demo Stores",
            businessGstin = "27AAACT1234A1Z6",
            asOfDate = "2025-09-30",
            party = party,
        )

    // ── placeholder resolution ───────────────────────────────────────────────

    @Test
    fun everyPlaceholderResolvesNoTokenLeftBehind() {
        val party = party(name = "Acme Traders", invoices = listOf(invoice("v-1", "INV-001", "2025-07-01", money(1000))))
        ReminderTone.entries.forEach { tone ->
            val letter = ReminderLetter.generate(input(party), tone)
            assertFalse(letter.contains("{"), "tone $tone left an unresolved placeholder in:\n$letter")
            assertTrue(letter.contains("Tanvrit Demo Stores"), "business name missing")
            assertTrue(letter.contains("Acme Traders"), "party name missing")
            assertTrue(letter.contains("27AAACT1234A1Z6"), "letterhead GSTIN missing")
            assertTrue(letter.contains("27AABCA1234Z1Z5"), "party GSTIN missing")
            assertTrue(letter.contains("2025-09-30"), "as-of date missing")
        }
    }

    @Test
    fun blankBusinessGstinOmitsTheLineInsteadOfBlankToken() {
        val party = party(invoices = listOf(invoice("v-1", "INV-001", "2025-07-01", money(100))))
        val letter = ReminderLetter.generate(input(party).copy(businessGstin = ""), ReminderTone.REMINDER)
        assertFalse(letter.contains("GSTIN: \n"))
        assertEquals(1, letter.lines().count { it.startsWith("GSTIN:") }, "only the party GSTIN line remains")
    }

    // ── invoice table + totals ───────────────────────────────────────────────

    @Test
    fun invoiceTableIsNumberedAndTotalsLineMatchesSum() {
        val party =
            party(
                invoices =
                    listOf(
                        invoice("v-1", "INV-001", "2025-07-01", money(1000), daysOverdue = 61),
                        invoice("v-2", "INV-002", "2025-08-01", money(250), daysOverdue = 29),
                    ),
            )
        val letter = ReminderLetter.generate(input(party), ReminderTone.REMINDER)
        assertTrue(letter.contains(" 1. INV-001"), "numbered first row missing:\n$letter")
        assertTrue(letter.contains(" 2. INV-002"), "numbered second row missing:\n$letter")
        val expectedTotal = formatMoney(money(1250))
        val totalsLine = letter.lines().single { it.startsWith("Total outstanding:") }
        assertTrue(totalsLine.contains(expectedTotal), "totals line missing $expectedTotal")
        assertTrue(totalsLine.contains("2 invoice(s)"))
    }

    // ── template catalog ─────────────────────────────────────────────────────

    @Test
    fun allThreeTonesProduceDistinctNonEmptyLetters() {
        val party = party(invoices = listOf(invoice("v-1", "INV-001", "2025-07-01", money(100))))
        val letters = ReminderTone.entries.associateWith { ReminderLetter.generate(input(party), it) }
        letters.values.forEach { assertTrue(it.length > 200, "suspiciously thin letter") }
        assertNotEquals(letters[ReminderTone.CONSERVATIVE], letters[ReminderTone.REMINDER])
        assertNotEquals(letters[ReminderTone.REMINDER], letters[ReminderTone.FINAL])
        assertTrue(ReminderLetter.subjectFor(input(party), ReminderTone.CONSERVATIVE).contains("Gentle reminder"))
        assertTrue(ReminderLetter.subjectFor(input(party), ReminderTone.FINAL).contains("Final notice"))
    }

    // ── PII discipline ───────────────────────────────────────────────────────

    @Test
    fun letterNeverLeaksInternalIdsOrUnmappedAccountFields() {
        // Build the party the way production does — AgingMath over real models —
        // with an account full of data the letter has no business repeating.
        val account =
            Account(
                accountCode = "2001",
                name = "Acme Traders",
                dimensions =
                    mapOf(
                        "gstin" to "27AABCA1234Z1Z5",
                        "email" to "accounts@acme.example",
                        "internalNote" to "DO-NOT-SHOW-9f8e7d",
                    ),
                taxConfig = AccountingTaxConfig(metadata = mapOf("phone" to "+91-99999-00000")),
            ).also { it.id = "cust-secret-id-42" }
        val voucher =
            Voucher(
                businessId = "biz-1",
                voucherNumber = "INV-001",
                voucherType = VoucherType.SALE,
                date = "2025-07-01",
                status = VoucherStatus.POSTED,
                lineItems =
                    listOf(
                        VoucherLineItem(accountId = account.id, accountName = "Acme Traders", debit = money(500)),
                        VoucherLineItem(accountId = "rev-1", accountName = "Sales revenue", credit = money(500)),
                    ),
            ).also { it.id = "v-secret-7" }

        val party = AgingMath.agingOf(listOf(voucher), mapOf(account.id to account), "2025-09-30").single()
        val letter = ReminderLetter.generate(input(party.copy(email = "")), ReminderTone.FINAL)

        // Intended account fields DO appear…
        assertTrue(letter.contains("Acme Traders"))
        assertTrue(letter.contains("27AABCA1234Z1Z5"))
        // …and nothing beyond them: no internal ids, no free-form dimension
        // values, no contact channel values in the body.
        assertFalse(letter.contains("cust-secret-id-42"), "account id leaked")
        assertFalse(letter.contains("DO-NOT-SHOW-9f8e7d"), "unmapped dimension value leaked")
        assertFalse(letter.contains("+91-99999-00000"), "phone leaked")
        assertFalse(letter.contains("accounts@acme.example"), "email leaked")
        assertFalse(letter.contains("v-secret-7"), "internal voucher id leaked when a number exists")
    }

    // ── mailto ───────────────────────────────────────────────────────────────

    @Test
    fun mailtoPrefillsRecipientSubjectAndEncodedBody() {
        val party = party(email = "accounts@acme.example", invoices = listOf(invoice("v-1", "INV-001", "2025-07-01", money(100))))
        val uri = ReminderLetter.mailtoFor(input(party), ReminderTone.REMINDER)
        assertTrue(uri.startsWith("mailto:accounts@acme.example?subject="), "recipient not prefilled: $uri")
        assertTrue(uri.contains("subject=Payment%20reminder"), "subject not encoded: $uri")
        assertTrue(uri.contains("&body="), "body missing")
        assertFalse(uri.substringAfter("&body=").contains("\n"), "raw newlines must be percent-encoded")
    }

    @Test
    fun mailtoWithoutPartyEmailStillOpensAComposer() {
        val party = party(invoices = listOf(invoice("v-1", "INV-001", "2025-07-01", money(100))))
        val uri = ReminderLetter.mailtoFor(input(party), ReminderTone.CONSERVATIVE)
        assertTrue(uri.startsWith("mailto:?subject="), uri)
    }

    @Test
    fun percentEncodingKeepsUnreservedAndEscapesTheRest() {
        assertEquals("plain-Text_1.~", ReminderLetter.encodeQueryParam("plain-Text_1.~"))
        assertEquals("a%0Ab%40c%20d", ReminderLetter.encodeQueryParam("a\nb@c d"))
        assertEquals("%E2%82%B9", ReminderLetter.encodeQueryParam("₹"), "UTF-8 must encode per byte")
    }
}
