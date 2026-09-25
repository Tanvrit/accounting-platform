package com.tanvrit.accounting.data

import com.tanvrit.core.di.TanvritKoin
import com.tanvrit.storage.store.UserDefaults
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Accounting-wide, app-local settings (numbering series, GST/TDS registration,
 * rounding, fiscal-year start). Persisted through the SDK [UserDefaults]
 * store, namespaced by appId (`com.tanvrit.accounting.<key>`), so values
 * survive restarts and never bleed across tenants.
 *
 * These mirror the server-side defaults the accounting module applies; the
 * server remains authoritative when a value is set there.
 */
class AccountingSettingsStore {
    private val defaults: UserDefaults = TanvritKoin.get()

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AccountingSettings> = _settings.asStateFlow()

    fun update(transform: (AccountingSettings) -> AccountingSettings) {
        val next = transform(_settings.value)
        persist(next)
        _settings.value = next
    }

    private fun load(): AccountingSettings =
        AccountingSettings(
            baseCurrency = defaults.retrieveScoped(KEY_BASE_CURRENCY).ifBlank { "INR" },
            fiscalYearStartMonth = defaults.retrieveScoped(KEY_FY_START).ifBlank { "4" }.toIntOrNull() ?: 4,
            gstin = defaults.retrieveScoped(KEY_GSTIN),
            tan = defaults.retrieveScoped(KEY_TAN),
            salesPrefix = defaults.retrieveScoped(KEY_PREFIX_SALES).ifBlank { "INV" },
            purchasePrefix = defaults.retrieveScoped(KEY_PREFIX_PURCHASE).ifBlank { "BILL" },
            receiptPrefix = defaults.retrieveScoped(KEY_PREFIX_RECEIPT).ifBlank { "RCPT" },
            paymentPrefix = defaults.retrieveScoped(KEY_PREFIX_PAYMENT).ifBlank { "PMT" },
            journalPrefix = defaults.retrieveScoped(KEY_PREFIX_JOURNAL).ifBlank { "JV" },
            einvoiceEnabled = defaults.retrieveScoped(KEY_EINVOICE) == "true",
            ewayBillEnabled = defaults.retrieveScoped(KEY_EWAY) == "true",
            autoFetchFxRates = defaults.retrieveScoped(KEY_FX_AUTO).ifBlank { "true" } == "true",
        )

    private fun persist(value: AccountingSettings) {
        defaults.storeScoped(KEY_BASE_CURRENCY, value.baseCurrency)
        defaults.storeScoped(KEY_FY_START, value.fiscalYearStartMonth.toString())
        defaults.storeScoped(KEY_GSTIN, value.gstin)
        defaults.storeScoped(KEY_TAN, value.tan)
        defaults.storeScoped(KEY_PREFIX_SALES, value.salesPrefix)
        defaults.storeScoped(KEY_PREFIX_PURCHASE, value.purchasePrefix)
        defaults.storeScoped(KEY_PREFIX_RECEIPT, value.receiptPrefix)
        defaults.storeScoped(KEY_PREFIX_PAYMENT, value.paymentPrefix)
        defaults.storeScoped(KEY_PREFIX_JOURNAL, value.journalPrefix)
        defaults.storeScoped(KEY_EINVOICE, value.einvoiceEnabled.toString())
        defaults.storeScoped(KEY_EWAY, value.ewayBillEnabled.toString())
        defaults.storeScoped(KEY_FX_AUTO, value.autoFetchFxRates.toString())
    }

    private companion object {
        const val KEY_BASE_CURRENCY = "accounting.baseCurrency"
        const val KEY_FY_START = "accounting.fiscalYearStartMonth"
        const val KEY_GSTIN = "accounting.gstin"
        const val KEY_TAN = "accounting.tan"
        const val KEY_PREFIX_SALES = "accounting.prefix.sales"
        const val KEY_PREFIX_PURCHASE = "accounting.prefix.purchase"
        const val KEY_PREFIX_RECEIPT = "accounting.prefix.receipt"
        const val KEY_PREFIX_PAYMENT = "accounting.prefix.payment"
        const val KEY_PREFIX_JOURNAL = "accounting.prefix.journal"
        const val KEY_EINVOICE = "accounting.einvoiceEnabled"
        const val KEY_EWAY = "accounting.ewayBillEnabled"
        const val KEY_FX_AUTO = "accounting.autoFetchFxRates"
    }
}

data class AccountingSettings(
    val baseCurrency: String = "INR",
    /** April = Indian fiscal year start. */
    val fiscalYearStartMonth: Int = 4,
    val gstin: String = "",
    val tan: String = "",
    val salesPrefix: String = "INV",
    val purchasePrefix: String = "BILL",
    val receiptPrefix: String = "RCPT",
    val paymentPrefix: String = "PMT",
    val journalPrefix: String = "JV",
    val einvoiceEnabled: Boolean = false,
    val ewayBillEnabled: Boolean = false,
    val autoFetchFxRates: Boolean = true,
)
