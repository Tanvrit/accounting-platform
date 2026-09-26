package com.tanvrit.accounting

import com.tanvrit.accounting.navigation.AppRoute
import com.tanvrit.accounting.screens.reconciliation.StatementCsvParser
import com.tanvrit.accounting.screens.voucherEntry.VoucherLineDraft
import com.tanvrit.core.feature.accounting.model.BankStatement
import com.tanvrit.core.feature.accounting.model.Voucher
import com.tanvrit.core.feature.accounting.model.VoucherLineItem
import com.tanvrit.core.feature.accounting.model.VoucherType
import com.tanvrit.core.feature.money.Money
import com.tanvrit.core.network.AppJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * kotlinx-serialization round trips through `AppJson.json` for the types this
 * app puts on the wire: its own @Serializable navigation routes, and the
 * accounting SDK models the voucher editor and the reconciliation importer
 * build (`Voucher` from line drafts, `BankStatement` from the CSV parser).
 *
 * Deliberate scope note: this app's OWN `data/` models (`AccountingSettings`)
 * are NOT @Serializable — there is no kotlinx path to round-trip them without
 * touching commonMain, which this safety-net task must not do. Their only
 * persistence path is `AccountingSettingsStore` ↔ UserDefaults, deliberately
 * skipped — see AccountingAppModuleTest for why.
 */
class SerializationRoundTripTest {
    // KEEP IN SYNC: mirrors the file-private `VoucherLineDraft.toModel()` at the
    // bottom of screens/voucherEntry/VoucherEntryViewModel.kt — private, so the
    // draft→wire mapping cannot be invoked from a test directly. If that
    // mapping changes, change this replica.
    private fun VoucherLineDraft.toModelReplica(): VoucherLineItem =
        VoucherLineItem(
            accountId = accountId,
            accountCode = accountCode,
            accountName = accountName,
            debit = Money.fromDouble(debitText.trim().replace(",", "").toDoubleOrNull() ?: 0.0),
            credit = Money.fromDouble(creditText.trim().replace(",", "").toDoubleOrNull() ?: 0.0),
            narration = narration.trim(),
        )

    @Test
    fun sealedRouteObjectsRoundTripWithDiscriminator() {
        val encoded = AppJson.json.encodeToString<AppRoute>(AppRoute.Dashboard)
        assertTrue(encoded.contains("Dashboard"), "expected a type discriminator in $encoded")
        assertEquals(AppRoute.Dashboard, AppJson.json.decodeFromString<AppRoute>(encoded))
        assertEquals(
            AppRoute.Reconciliation,
            AppJson.json.decodeFromString<AppRoute>(AppJson.json.encodeToString<AppRoute>(AppRoute.Reconciliation)),
        )
    }

    @Test
    fun voucherEntryRouteRoundTripsAndOmitsDefaultValue() {
        val purchase = AppRoute.VoucherEntry("PURCHASE")
        val encoded = AppJson.json.encodeToString<AppRoute>(purchase)
        assertEquals(purchase, AppJson.json.decodeFromString<AppRoute>(encoded))

        // AppJson.json is encodeDefaults=false: a default-voucherType route
        // emits no "voucherType" key and decodes back to "SALE".
        val defaultEncoded = AppJson.json.encodeToString<AppRoute>(AppRoute.VoucherEntry())
        assertFalse(defaultEncoded.contains("voucherType"), "default field must be omitted: $defaultEncoded")
        assertEquals(AppRoute.VoucherEntry("SALE"), AppJson.json.decodeFromString<AppRoute>(defaultEncoded))
    }

    @Test
    fun voucherLineDraftMappingProducesWireStableItem() {
        val draft =
            VoucherLineDraft(
                localId = "line-1",
                accountId = "acc-1",
                accountCode = "1000",
                accountName = "Cash",
                debitText = "12,500.50",
                creditText = "",
                narration = "  April rent  ",
            )
        val item = draft.toModelReplica()
        assertEquals(Money.fromDouble(12500.50), item.debit)
        assertEquals(Money.ZERO, item.credit)
        assertEquals("April rent", item.narration)

        val encoded = AppJson.json.encodeToString(VoucherLineItem.serializer(), item)
        // MoneySerializer writes raw minor-unit Longs; the sheet stays integer.
        assertTrue(encoded.contains("\"debit\":1250050"), "expected minor-unit debit in $encoded")
        // encodeDefaults=false: the ZERO credit is omitted and decodes back to ZERO.
        assertFalse(encoded.contains("\"credit\""), "ZERO credit must be omitted: $encoded")

        val decoded = AppJson.json.decodeFromString(VoucherLineItem.serializer(), encoded)
        assertEquals(item, decoded)
        assertEquals(1250050L, decoded.debit.amountInSmallestUnit)
    }

    @Test
    fun voucherEnvelopeRoundTrips() {
        val voucher =
            Voucher(
                businessId = "biz-1",
                voucherType = VoucherType.SALE,
                date = "2026-04-05",
                narration = "April rent",
                referenceId = "NEFT123",
                lineItems =
                    listOf(
                        VoucherLineItem(
                            accountId = "acc-cash",
                            accountName = "Cash",
                            debit = Money.fromDouble(45000.00),
                        ),
                        VoucherLineItem(
                            accountId = "acc-rent",
                            accountName = "Rent expense",
                            credit = Money.fromDouble(45000.00),
                        ),
                    ),
            )
        val encoded = AppJson.json.encodeToString(Voucher.serializer(), voucher)
        val decoded = AppJson.json.decodeFromString(Voucher.serializer(), encoded)
        assertEquals(voucher, decoded)
        // BaseDataClass envelope fields are body vars but still on the wire.
        assertEquals(voucher.id, decoded.id)
        assertEquals(voucher.createdAt, decoded.createdAt)
    }

    @Test
    fun parsedBankStatementRoundTrips() {
        val raw =
            """
            2025-04-05, NEFT RENT APR, -45000.00, 312500.00, NEFT123, DEBIT
            2025-04-07, UPI SALES, 60000.00, 372500.00, UPI456, CREDIT
            """.trimIndent()
        val (statement, errors) = StatementCsvParser.toStatement("acc-1", raw)
        assertTrue(errors.isEmpty(), "unexpected parse errors: $errors")
        requireNotNull(statement)

        val encoded = AppJson.json.encodeToString(BankStatement.serializer(), statement)
        val decoded = AppJson.json.decodeFromString(BankStatement.serializer(), encoded)
        assertEquals(statement, decoded)
        assertEquals(statement.id, decoded.id)
        assertEquals(2, decoded.transactions.size)
        assertEquals(Money.fromDouble(372500.00), decoded.closingBalance)
    }
}
