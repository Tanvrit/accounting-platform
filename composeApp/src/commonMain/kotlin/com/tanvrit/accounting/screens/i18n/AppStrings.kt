package com.tanvrit.accounting.screens.i18n

import kotlinx.serialization.Serializable

/**
 * UI language (roadmap #15). Only [code] is persisted — via
 * `data/LocaleStore` under the `accounting.locale` UserDefaults key.
 */
@Serializable
enum class AppLanguage(
    val code: String,
    val label: String,
) {
    EN("en", "English"),
    HI("hi", "हिन्दी"),
    ;

    companion object {
        /** Unknown/blank codes fall back to [EN] — never throws. */
        fun fromCode(code: String): AppLanguage = entries.firstOrNull { it.code == code } ?: EN
    }
}

/**
 * String tables + lookup (roadmap #15 — i18n foundation).
 *
 * v1 SCOPE (honesty note): only the top-level nav labels, a few common
 * actions and the keyboard-cheat-sheet title/subtitle are localized through
 * this table. Every screen body stays English in v1; full coverage is the
 * roadmap #15 follow-up.
 *
 * Keys use a stable `area.name` convention and values never carry
 * placeholders (`%s`, `{0}`) — the AppStringsTest parity/placeholder pins
 * enforce both.
 */
object Strings {
    val EN: Map<String, String> =
        mapOf(
            "nav.dashboard" to "Dashboard",
            "nav.accounts" to "Accounts",
            "nav.voucher" to "Voucher",
            "nav.gst" to "GST",
            "nav.tds" to "TDS",
            "nav.reports" to "Reports",
            "nav.periods" to "Periods",
            "nav.budget" to "Budget",
            "nav.bankrec" to "Bank Rec",
            "nav.audit" to "Audit",
            "nav.dunning" to "Dunning",
            "nav.recurring" to "Recurring",
            "nav.currency" to "Currency",
            "nav.import" to "Import",
            "nav.assets" to "Assets",
            "nav.consolidate" to "Consolidate",
            "nav.payroll" to "Payroll",
            "nav.settings" to "Settings",
            "common.refresh" to "Refresh",
            "common.back" to "Back",
            "common.loading" to "Loading…",
            "common.error_retry" to "Something failed — retry",
            "cheat.title" to "Keyboard shortcuts",
            "cheat.subtitle" to "Tally-style speed entry. Desktop and web only (hardware keyboard).",
        )

    val HI: Map<String, String> =
        mapOf(
            "nav.dashboard" to "डैशबोर्ड",
            "nav.accounts" to "खाते",
            "nav.voucher" to "वाउचर",
            "nav.gst" to "जीएसटी",
            "nav.tds" to "टीडीएस",
            "nav.reports" to "रिपोर्ट",
            "nav.periods" to "अवधियाँ",
            "nav.budget" to "बजट",
            "nav.bankrec" to "बैंक मिलान",
            "nav.audit" to "ऑडिट",
            "nav.dunning" to "बकाया अनुस्मारक",
            "nav.recurring" to "आवर्ती",
            "nav.currency" to "मुद्रा",
            "nav.import" to "आयात",
            "nav.assets" to "संपत्तियाँ",
            "nav.consolidate" to "समेकन",
            "nav.payroll" to "पेरोल",
            "nav.settings" to "सेटिंग्स",
            "common.refresh" to "रीफ्रेश",
            "common.back" to "वापस",
            "common.loading" to "लोड हो रहा है…",
            "common.error_retry" to "कुछ गड़बड़ हुई — पुनः प्रयास करें",
            "cheat.title" to "कीबोर्ड शॉर्टकट",
            "cheat.subtitle" to "टैली-शैली तीव्र प्रविष्टि। केवल डेस्कटॉप और वेब (हार्डवेयर कीबोर्ड)।",
        )

    /**
     * Lookup with two-step fallback: [lang] table → [EN] → the key itself
     * (visible, so a missing translation can never crash a screen).
     */
    fun get(
        lang: AppLanguage,
        key: String,
    ): String =
        when (lang) {
            AppLanguage.EN -> EN
            AppLanguage.HI -> HI
        }[key] ?: EN[key] ?: key
}
