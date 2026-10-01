package com.tanvrit.accounting.screens.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Roadmap #15 i18n foundation pins: EN/HI parity, no placeholders, stable codes. */
class AppStringsTest {
    @Test
    fun englishAndHindiTablesHaveIdenticalKeySets() {
        assertEquals(
            Strings.EN.keys.sorted(),
            Strings.HI.keys.sorted(),
            "EN/HI tables diverged — missing EN keys: ${Strings.HI.keys - Strings.EN.keys}, " +
                "missing HI keys: ${Strings.EN.keys - Strings.HI.keys}",
        )
    }

    @Test
    fun allValuesAreNonBlankAndPlaceholderFree() {
        (Strings.EN.entries + Strings.HI.entries).forEach { (key, value) ->
            assertTrue(value.isNotBlank(), "$key has a blank value")
            assertFalse(value.contains("%s"), "$key carries a %s placeholder: $value")
            assertFalse(value.contains("%d"), "$key carries a %d placeholder: $value")
            assertFalse(Regex("""\{\d*\}""").containsMatchIn(value), "$key carries a {n} placeholder: $value")
        }
    }

    @Test
    fun languageCodesAreUnique() {
        assertEquals(
            AppLanguage.entries.size,
            AppLanguage.entries
                .map { it.code }
                .distinct()
                .size,
            "duplicate AppLanguage.code",
        )
    }

    @Test
    fun unknownLanguageCodeFallsBackToEnglish() {
        assertEquals(AppLanguage.EN, AppLanguage.fromCode(""))
        assertEquals(AppLanguage.EN, AppLanguage.fromCode("fr"))
        assertEquals(AppLanguage.HI, AppLanguage.fromCode("hi"))
    }

    @Test
    fun lookupResolvesHindiAndFallsBackToTheKeyItself() {
        assertEquals(Strings.HI["cheat.title"], Strings.get(AppLanguage.HI, "cheat.title"))
        assertEquals(Strings.EN["nav.dashboard"], Strings.get(AppLanguage.EN, "nav.dashboard"))
        // Missing key resolves to the key itself — visible, never a crash.
        assertEquals("nav.nonexistent", Strings.get(AppLanguage.HI, "nav.nonexistent"))
    }
}
