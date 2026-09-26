package com.tanvrit.accounting.data

import com.tanvrit.core.di.TanvritKoin
import com.tanvrit.core.feature.accounting.model.VoucherType
import com.tanvrit.core.network.AppJson
import com.tanvrit.storage.store.UserDefaults
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/**
 * One numbering series for a voucher type: `"<prefix><zero-padded number><suffix>"`.
 * Client-local only (roadmap #10) — the server mints its own number on create;
 * this series drives the number the entry screen shows and assigns to
 * offline-persisted drafts. Persisted per business under
 * `voucherNumbering.<businessId>` via [UserDefaults] (namespaced by appId).
 */
@Serializable
data class NumberingSeries(
    val prefix: String = "",
    /** The next number to hand out; 1-based. */
    val nextNumber: Int = 1,
    /** Zero-padding width for the numeric part ("0007" at width 4). */
    val width: Int = DEFAULT_WIDTH,
    val suffix: String = "",
) {
    private companion object {
        const val DEFAULT_WIDTH = 4
    }
}

/**
 * Pure numbering logic — no Koin, no storage — so it is fully commonTest-able.
 */
object VoucherNumbering {
    const val MIN_WIDTH = 1
    const val MAX_WIDTH = 12

    /** Voucher types the tabbed composer (and the settings card) offer. */
    val editableTypes: List<VoucherType> =
        listOf(
            VoucherType.SALE,
            VoucherType.PURCHASE,
            VoucherType.RECEIPT,
            VoucherType.PAYMENT,
            VoucherType.JOURNAL,
            VoucherType.CONTRA,
        )

    /** Renders `prefix` + `nextNumber` zero-padded to `width` + `suffix`. */
    fun format(series: NumberingSeries): String {
        val padded = series.nextNumber.toString().padStart(series.width.coerceIn(MIN_WIDTH, MAX_WIDTH), '0')
        return "${series.prefix}$padded${series.suffix}"
    }

    /** The series after one number was consumed — prefix/width/suffix preserved. */
    fun incremented(series: NumberingSeries): NumberingSeries = series.copy(nextNumber = series.nextNumber + 1)

    /** Clamps user-edited values into range; never throws. */
    fun sanitized(series: NumberingSeries): NumberingSeries =
        series.copy(
            nextNumber = series.nextNumber.coerceAtLeast(1),
            width = series.width.coerceIn(MIN_WIDTH, MAX_WIDTH),
        )

    /**
     * Default series for a type the user has not configured yet: the prefix
     * falls back to the legacy "Numbering series" prefixes in
     * [AccountingSettings] where one exists, else the type code itself.
     */
    fun defaultFor(
        type: VoucherType,
        settings: AccountingSettings = AccountingSettings(),
    ): NumberingSeries =
        NumberingSeries(
            prefix =
                when (type) {
                    VoucherType.SALE -> settings.salesPrefix
                    VoucherType.PURCHASE -> settings.purchasePrefix
                    VoucherType.RECEIPT -> settings.receiptPrefix
                    VoucherType.PAYMENT -> settings.paymentPrefix
                    VoucherType.JOURNAL -> settings.journalPrefix
                    else -> type.code
                },
        )
}

/**
 * Per-business voucher numbering series store (feature #10). Same persistence
 * shape as [AccountingSettingsStore]: [UserDefaults] + a [StateFlow] that
 * screens collect. The active business is set via [selectBusiness] by the
 * consuming ViewModel (driven by `AccountingWorkspace.businessId`).
 */
class NumberingSeriesStore {
    private val defaults: UserDefaults = TanvritKoin.get()
    private val settingsStore: AccountingSettingsStore = TanvritKoin.get()

    private var activeBusinessId: String = ""

    /** Type code ("SALE", …) → series, for the active business. */
    private val _series = MutableStateFlow<Map<String, NumberingSeries>>(emptyMap())
    val series: StateFlow<Map<String, NumberingSeries>> = _series.asStateFlow()

    fun selectBusiness(businessId: String) {
        if (businessId == activeBusinessId) return
        activeBusinessId = businessId
        _series.value = load(businessId)
    }

    /** Persisted series for [type], or the settings-seeded default. */
    fun seriesFor(type: VoucherType): NumberingSeries =
        _series.value[type.code] ?: VoucherNumbering.defaultFor(type, settingsStore.settings.value)

    /** The next formatted number, WITHOUT advancing the series. */
    fun peekNumber(type: VoucherType): String = VoucherNumbering.format(seriesFor(type))

    /** The next formatted number; the series advances immediately. */
    fun consumeNumber(type: VoucherType): String {
        val current = seriesFor(type)
        updateSeries(type, VoucherNumbering.incremented(current))
        return VoucherNumbering.format(current)
    }

    fun updateSeries(
        type: VoucherType,
        series: NumberingSeries,
    ) {
        val next = _series.value + (type.code to VoucherNumbering.sanitized(series))
        persist(next)
        _series.value = next
    }

    private fun key() = "$KEY_BASE.$activeBusinessId"

    private fun load(businessId: String): Map<String, NumberingSeries> {
        if (businessId.isBlank()) return emptyMap()
        val raw = defaults.retrieveScoped("$KEY_BASE.$businessId")
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            AppJson.json.decodeFromString<Map<String, NumberingSeries>>(raw)
        }.getOrDefault(emptyMap())
    }

    private fun persist(value: Map<String, NumberingSeries>) {
        if (activeBusinessId.isBlank()) return
        defaults.storeScoped(key(), AppJson.json.encodeToString(value))
    }

    private companion object {
        /** Prefix of the namespaced key — `"$KEY_BASE.<businessId>"`. */
        const val KEY_BASE = "voucherNumbering"
    }
}
