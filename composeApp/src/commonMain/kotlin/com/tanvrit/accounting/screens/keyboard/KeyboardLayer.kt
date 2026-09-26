package com.tanvrit.accounting.screens.keyboard

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Compose bridge for [ShortcutRegistry]: maps a hardware key event to a
 * normalized chord string (see [keyEventChord]) consumed by
 * [Modifier.tanvritShortcutLayer], which hooks the preview phase so shortcuts
 * still fire while a text field is focused (Tally-style speed entry — the
 * point of roadmap #12).
 *
 * Fully wired on desktop + wasmJs/browser. Compiles identically on
 * android/ios and simply never fires without a hardware keyboard attached.
 *
 * Returns the normalized chord for a KEY_DOWN event, or null when the event
 * is ignored.
 */
fun keyEventChord(event: KeyEvent): String? {
    if (event.type != KeyEventType.KeyDown) return null
    val hasPrimaryModifier = event.isCtrlPressed || event.isMetaPressed || event.isAltPressed
    val mods =
        buildList {
            if (event.isCtrlPressed) add("CTRL")
            if (event.isMetaPressed) add("META")
            if (event.isAltPressed) add("ALT")
            // SHIFT is recorded only when it's the sole modifier, so "Shift+/" (the
            // '?' cheat-sheet chord) works while plain typing never collides.
            if (event.isShiftPressed && !hasPrimaryModifier) add("SHIFT")
        }
    val name = keyNameOf(event.key) ?: return null
    return ShortcutRegistry.normalize((mods + name).joinToString("+"))
}

private fun keyNameOf(key: Key): String? =
    when (key) {
        Key.A -> "A"
        Key.B -> "B"
        Key.C -> "C"
        Key.D -> "D"
        Key.E -> "E"
        Key.F -> "F"
        Key.G -> "G"
        Key.H -> "H"
        Key.I -> "I"
        Key.J -> "J"
        Key.K -> "K"
        Key.L -> "L"
        Key.M -> "M"
        Key.N -> "N"
        Key.O -> "O"
        Key.P -> "P"
        Key.Q -> "Q"
        Key.R -> "R"
        Key.S -> "S"
        Key.T -> "T"
        Key.U -> "U"
        Key.V -> "V"
        Key.W -> "W"
        Key.X -> "X"
        Key.Y -> "Y"
        Key.Z -> "Z"
        Key.Enter, Key.NumPadEnter -> "ENTER"
        Key.Escape -> "ESCAPE"
        Key.Slash -> "/"
        else -> null
    }

/**
 * Preview-phase key handler. Returns true when the chord consumed the event.
 * [onChord] responders return true when they handled the chord.
 */
fun Modifier.tanvritShortcutLayer(
    enabled: Boolean = true,
    onChord: (String) -> Boolean,
): Modifier =
    onPreviewKeyEvent { event ->
        if (!enabled) return@onPreviewKeyEvent false
        val chord = keyEventChord(event) ?: return@onPreviewKeyEvent false
        onChord(chord)
    }
