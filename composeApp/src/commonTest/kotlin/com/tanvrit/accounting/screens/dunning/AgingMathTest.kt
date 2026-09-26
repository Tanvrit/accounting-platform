package com.tanvrit.accounting.screens.dunning

import com.tanvrit.core.feature.accounting.model.Account
import com.tanvrit.core.feature.accounting.model.Voucher
import com.tanvrit.core.feature.accounting.model.VoucherLineItem
import com.tanvrit.core.feature.accounting.model.VoucherStatus
import com.tanvrit.core.feature.accounting.model.VoucherType
import com.tanvrit.core.feature.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgingMathTest {
    /** Minor units straight from paise — no Double rounding in fixtures. */
    private fun money(rupees: Long): Money = Money.fromSmallestUnit(rupees * 100)

    private fun account(
        id: String,
        code: String,
        name: String,
        dimensions: Map<String, String> = emptyMap(),
    ): Account = Account(accountCode = code, name = name, dimensions = dimensions).also { it.id = id }

    /**
     * A balanced POSTED SALE: [debits] keyed by account id, each leg offset by
     * a single revenue credit leg so the fixture double-entries balance.
     */
    private fun sale(
        id: String,
        number: String,
        date: String,
        debits: Map<String, Money>,
        status: VoucherStatus = VoucherStatus.POSTED,
        dimensions: Map<String, String> = emptyMap(),
        names: Map<String, String> = emptyMap(),
    ): Voucher {
        val total = debits.values.fold(Money.ZERO) { acc, leg -> acc + leg }
        val legs =
            debits.entries.mapIndexed { index, (accountId, amount) ->
                VoucherLineItem(
                    accountId = accountId,
                    accountCode = names["code:$accountId"] ?: "1${index}00",
                    accountName = names[accountId] ?: "Leg $accountId",
                    debit = amount,
                )
            } +
                VoucherLineItem(
                    accountId = "rev-1",
                    accountCode = "4000",
                    accountName = "Sales revenue",
                    credit = total,
                )
        return Voucher(
            businessId = "biz-1",
            voucherNumber = number,
            voucherType = VoucherType.SALE,
            date = date,
            status = status,
            lineItems = legs,
            dimensions = dimensions,
        ).also { it.id = id }
    }

    private val accounts =
        listOf(
            account("acme", "2001", "Acme Traders"),
            account("beta", "2002", "Beta Stores", dimensions = mapOf("termsDays" to "45")),
            account("cash", "1001", "Cash"),
        )

    // ── bucket boundaries ────────────────────────────────────────────────────

    @Test
    fun bucketEdgesThirtyVersusThirtyOne() {
        assertEquals(AgingBucket.DAYS_0_30, AgingMath.bucketOf(0))
        assertEquals(AgingBucket.DAYS_0_30, AgingMath.bucketOf(30))
        assertEquals(AgingBucket.DAYS_31_60, AgingMath.bucketOf(31))
        assertEquals(AgingBucket.DAYS_31_60, AgingMath.bucketOf(60))
        assertEquals(AgingBucket.DAYS_61_90, AgingMath.bucketOf(61))
        assertEquals(AgingBucket.DAYS_61_90, AgingMath.bucketOf(90))
        assertEquals(AgingBucket.DAYS_91_PLUS, AgingMath.bucketOf(91))
    }

    @Test
    fun bucketEdgesFlowThroughAging() {
        // termsDays = 0, reference 2025-09-30:
        // invoice 2025-08-31 is exactly 30 days overdue → 0–30; 2025-08-30 is 31 → 31–60.
        val parties =
            AgingMath.agingOf(
                listOf(
                    sale("v-1", "INV-1", "2025-08-31", mapOf("acme" to money(10)), dimensions = mapOf("termsDays" to "0")),
                    sale("v-2", "INV-2", "2025-08-30", mapOf("acme" to money(10)), dimensions = mapOf("termsDays" to "0")),
                ),
                accounts.associateBy { it.id },
                referenceDate = "2025-09-30",
            )
        val party = parties.single()
        assertEquals(1, party.bucketCount(AgingBucket.DAYS_0_30))
        assertEquals(1, party.bucketCount(AgingBucket.DAYS_31_60))
        assertEquals(money(10), party.bucketAmount(AgingBucket.DAYS_31_60))
    }

    // ── terms resolution ─────────────────────────────────────────────────────

    @Test
    fun missingTermsDaysDefaultsToThirty() {
        val voucher = sale("v-1", "INV-1", "2025-07-01", mapOf("acme" to money(10)))
        assertEquals(AgingMath.DEFAULT_TERMS_DAYS, AgingMath.termsDaysOf(voucher, accounts[0]))
        // Default 30: 2025-07-01 + 30d = 2025-07-31 due; as of 2025-08-30 → exactly 30 overdue.
        assertEquals(30, AgingMath.daysOverdue("2025-07-01", AgingMath.DEFAULT_TERMS_DAYS, "2025-08-30"))
        val parties = AgingMath.agingOf(listOf(voucher), accounts.associateBy { it.id }, "2025-08-30")
        assertEquals(1, parties.single().bucketCount(AgingBucket.DAYS_0_30))
        // One day earlier invoice crosses into 31–60.
        val earlier = sale("v-2", "INV-2", "2025-06-30", mapOf("acme" to money(10)))
        val partiesEarlier = AgingMath.agingOf(listOf(earlier), accounts.associateBy { it.id }, "2025-08-30")
        assertEquals(1, partiesEarlier.single().bucketCount(AgingBucket.DAYS_31_60))
    }

    @Test
    fun termsComeFromVoucherThenAccountThenDefault() {
        val voucher = sale("v-1", "INV-1", "2025-07-01", mapOf("acme" to money(10)), dimensions = mapOf("termsDays" to "7"))
        assertEquals(7, AgingMath.termsDaysOf(voucher, accounts[0]))
        // No voucher key → account dimensions win.
        val plain = sale("v-2", "INV-2", "2025-07-01", mapOf("beta" to money(10)))
        assertEquals(45, AgingMath.termsDaysOf(plain, accounts[1]))
        // Unknown account → default.
        assertEquals(30, AgingMath.termsDaysOf(plain, null))
        // Garbage values fall back to the default, never crash.
        val garbage = sale("v-3", "INV-3", "2025-07-01", mapOf("acme" to money(10)), dimensions = mapOf("termsDays" to "_"))
        assertEquals(30, AgingMath.termsDaysOf(garbage, null))
    }

    // ── receivable classification ────────────────────────────────────────────

    @Test
    fun onlyPostedSalesCount() {
        val parties =
            AgingMath.agingOf(
                listOf(
                    sale("v-1", "INV-1", "2025-08-01", mapOf("acme" to money(10)), status = VoucherStatus.DRAFT),
                    sale("v-2", "INV-2", "2025-08-01", mapOf("acme" to money(10)), status = VoucherStatus.CANCELLED),
                    sale("v-3", "INV-3", "2025-08-01", mapOf("acme" to money(10)), status = VoucherStatus.REVERSED),
                    sale("v-4", "INV-4", "2025-08-01", mapOf("acme" to money(10))),
                ),
                accounts.associateBy { it.id },
                "2025-09-30",
            )
        assertEquals(1, parties.single().invoiceCount)
    }

    @Test
    fun counterCashSalesAreNotReceivables() {
        val parties =
            AgingMath.agingOf(
                listOf(
                    sale("v-1", "CS-1", "2025-08-01", mapOf("cash" to money(10)), names = mapOf("cash" to "Cash")),
                ),
                accounts.associateBy { it.id },
                "2025-09-30",
            )
        assertTrue(parties.isEmpty(), "a sale debiting a cash-named account is a counter sale, not a receivable")
    }

    @Test
    fun zeroDebitLegsAndZeroBalancePartiesAreExcluded() {
        // A sale whose only receivable leg is zero has nothing to aging-track,
        // and the "party" behind it must NOT appear with a ₹0 total.
        val parties =
            AgingMath.agingOf(
                listOf(
                    sale("v-1", "INV-1", "2025-08-01", mapOf("acme" to Money.ZERO)),
                ),
                accounts.associateBy { it.id },
                "2025-09-30",
            )
        assertTrue(parties.isEmpty())
    }

    // ── aggregation ──────────────────────────────────────────────────────────

    @Test
    fun multiPartyAggregationSplitsBucketsAndTotals() {
        val parties =
            AgingMath.agingOf(
                listOf(
                    sale("v-1", "INV-1", "2025-08-15", mapOf("acme" to money(100))),
                    sale("v-2", "INV-2", "2025-07-01", mapOf("acme" to money(50))),
                    sale("v-3", "INV-3", "2025-01-01", mapOf("beta" to money(200))),
                ),
                accounts.associateBy { it.id },
                referenceDate = "2025-09-30",
            )
        assertEquals(2, parties.size)
        // Beta's 200 trumps Acme's 150 → sorted by total descending.
        val beta = parties[0]
        val acme = parties[1]
        assertEquals("beta", beta.partyAccountId)
        assertEquals("Beta Stores", beta.name)
        assertEquals(money(200), beta.total)
        assertEquals(1, beta.bucketCount(AgingBucket.DAYS_91_PLUS))
        assertEquals(money(150), acme.total)
        assertEquals(2, acme.invoiceCount)
        assertEquals("2025-08-15", acme.lastInvoiceDate)
        // Acme with default 30d terms: INV-2 due 2025-07-31 → 61 days overdue at 2025-09-30 (bucket 61–90),
        // INV-1 due 2025-09-14 → 16 days (bucket 0–30).
        assertEquals(money(100), acme.bucketAmount(AgingBucket.DAYS_0_30))
        assertEquals(money(50), acme.bucketAmount(AgingBucket.DAYS_61_90))
        // Summary strip reconciles with the party rows.
        val summaries = AgingMath.summaries(parties)
        assertEquals(4, summaries.size)
        assertEquals(money(350), summaries.fold(Money.ZERO) { acc, summary -> acc + summary.total })
    }

    @Test
    fun samePartyLegsWithinOneVoucherMerge() {
        val voucher =
            Voucher(
                businessId = "biz-1",
                voucherNumber = "INV-9",
                voucherType = VoucherType.SALE,
                date = "2025-08-01",
                status = VoucherStatus.POSTED,
                lineItems =
                    listOf(
                        VoucherLineItem(accountId = "acme", accountName = "Acme Traders", debit = money(60)),
                        VoucherLineItem(accountId = "acme", accountName = "Acme Traders", debit = money(40)),
                        VoucherLineItem(accountId = "rev-1", accountName = "Sales revenue", credit = money(100)),
                    ),
            ).also { it.id = "v-9" }
        val party = AgingMath.agingOf(listOf(voucher), accounts.associateBy { it.id }, "2025-09-30").single()
        assertEquals(1, party.invoiceCount)
        assertEquals(money(100), party.total)
    }

    // ── date edge behaviour ──────────────────────────────────────────────────

    @Test
    fun invoicesNotYetDueClampToCurrent() {
        assertEquals(0, AgingMath.daysOverdue("2025-09-25", 30, "2025-09-30"))
        val parties =
            AgingMath.agingOf(
                listOf(sale("v-1", "INV-1", "2025-09-25", mapOf("acme" to money(10)))),
                accounts.associateBy { it.id },
                "2025-09-30",
            )
        val invoice = parties.single().invoices.single()
        assertEquals(0, invoice.daysOverdue)
        assertEquals(AgingBucket.DAYS_0_30, invoice.bucket)
        assertEquals("2025-10-25", invoice.dueDate)
    }

    @Test
    fun unparseableDatesNeverCrashAndNeverReDate() {
        assertNull(AgingMath.daysOverdue("not-a-date", 30, "2025-09-30"))
        assertNull(AgingMath.dueDateOf("", 30))
        val parties =
            AgingMath.agingOf(
                listOf(sale("v-1", "INV-1", "01/08/2025", mapOf("acme" to money(10)))),
                accounts.associateBy { it.id },
                "2025-09-30",
            )
        val invoice = parties.single().invoices.single()
        assertEquals(0, invoice.daysOverdue)
        assertEquals("", invoice.dueDate)
    }

    // ── filters ──────────────────────────────────────────────────────────────

    @Test
    fun filtersMinAmountBucketAndQuery() {
        val parties =
            AgingMath.agingOf(
                listOf(
                    sale("v-1", "INV-1", "2025-01-01", mapOf("acme" to money(100))),
                    sale("v-2", "INV-2", "2025-09-20", mapOf("beta" to money(5))),
                ),
                accounts.associateBy { it.id },
                referenceDate = "2025-09-30",
            )
        assertEquals(1, AgingMath.applyFilters(parties, minAmount = money(10), bucket = null, query = "").size)
        assertEquals(1, AgingMath.applyFilters(parties, Money.ZERO, AgingBucket.DAYS_91_PLUS, "").size)
        assertEquals(1, AgingMath.applyFilters(parties, Money.ZERO, null, "stores").size)
        assertEquals("acme", AgingMath.applyFilters(parties, Money.ZERO, null, "2001").single().partyAccountId)
        assertTrue(AgingMath.applyFilters(parties, Money.ZERO, null, "zzz").isEmpty())
    }
}
