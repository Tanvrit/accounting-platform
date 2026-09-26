package com.tanvrit.accounting.screens.keyboard

/**
 * One keyboard shortcut. [chord] is normalized (see [ShortcutRegistry.normalize]):
 * canonical modifier order `Ctrl+Meta+Alt+Shift` then the key, e.g. `ALT+C`,
 * `CTRL+S`, `ALT+ENTER`, `ESCAPE`, `SHIFT+/` (rendered to the user as printed).
 */
data class Shortcut(
    val chord: String,
    val actionId: String,
    val label: String,
    val group: String,
)

/**
 * Small pure-Kotlin registry for app-wide shortcuts (roadmap #12). The Compose
 * layer ([Modifier.tanvritShortcutLayer]) maps a fired key event to a chord
 * string; this class resolves the chord to an action id. Pure and commonTest-able.
 */
class ShortcutRegistry {
    private val byChord = linkedMapOf<String, Shortcut>()

    val shortcuts: List<Shortcut>
        get() = byChord.values.toList()

    /** Registers [chord] → [actionId]. Throws on a conflicting chord (fail fast at startup, not at runtime). */
    fun register(
        chord: String,
        actionId: String,
        label: String,
        group: String,
    ) {
        val normalized = normalize(chord)
        require(!byChord.containsKey(normalized)) { "Duplicate shortcut registration: $normalized" }
        byChord[normalized] = Shortcut(normalized, actionId, label, group)
    }

    fun forChord(chord: String): Shortcut? = byChord[normalize(chord)]

    fun groups(): Map<String, List<Shortcut>> = byChord.values.groupBy { it.group }

    companion object {
        private val MODIFIER_ORDER = listOf("CTRL", "META", "ALT", "SHIFT")

        /** Canonicalizes "alt+c" / "Alt + C" / "ALT+C" → "ALT+C"; "Ctrl+Shift+Enter" → "CTRL+SHIFT+ENTER". */
        fun normalize(chord: String): String {
            val parts =
                chord
                    .split('+')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toMutableList()
            require(parts.isNotEmpty()) { "Empty chord" }
            val modifiers = linkedSetOf<String>()
            var key = ""
            for (part in parts) {
                val upper = part.uppercase()
                when (upper) {
                    "CTRL",
                    "CONTROL",
                    "CMD",
                    "COMMAND",
                    "META",
                    ->
                        modifiers +=
                            if (upper == "CONTROL") {
                                "CTRL"
                            } else if (upper == "CMD" || upper == "COMMAND") {
                                "META"
                            } else {
                                upper
                            }

                    "ALT",
                    "OPTION",
                    -> modifiers += "ALT"

                    "SHIFT" -> modifiers += "SHIFT"
                    else -> {
                        require(key.isEmpty()) { "Chord has two keys: '$chord'" }
                        key = canonicalKey(upper)
                    }
                }
            }
            require(key.isNotEmpty()) { "Chord has no key: '$chord'" }
            val orderedMods = MODIFIER_ORDER.filter { it in modifiers }
            return (orderedMods + key).joinToString("+")
        }

        private fun canonicalKey(upper: String): String =
            when (upper) {
                "ESC" -> "ESCAPE"
                "RETURN" -> "ENTER"
                "SLASH", "/" -> "/"
                else -> upper
            }
    }
}
