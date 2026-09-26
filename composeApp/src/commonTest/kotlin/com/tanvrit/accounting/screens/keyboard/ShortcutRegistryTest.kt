package com.tanvrit.accounting.screens.keyboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShortcutRegistryTest {
    private fun registry(vararg chords: String): ShortcutRegistry =
        ShortcutRegistry().apply {
            chords.forEachIndexed { index, chord ->
                register(chord, "a$index", "Action $index", "Group $index")
            }
        }

    @Test
    fun `normalize canonicalizes case order and aliases`() {
        assertEquals("ALT+C", ShortcutRegistry.normalize("alt+c"))
        assertEquals("ALT+C", ShortcutRegistry.normalize("c+alt"))
        assertEquals("CTRL+SHIFT+ENTER", ShortcutRegistry.normalize("shift+ctrl+enter"))
        assertEquals("META+K", ShortcutRegistry.normalize("cmd+k"))
        assertEquals("ALT+O", ShortcutRegistry.normalize("option+o"))
        assertEquals("ESCAPE", ShortcutRegistry.normalize("esc"))
        assertEquals("SHIFT+/", ShortcutRegistry.normalize("shift+slash"))
    }

    @Test
    fun `duplicate chord registration fails fast`() {
        assertFailsWith<IllegalArgumentException> {
            registry("ALT+C").apply { register("alt+c", "a2", "Dup", "G") }
        }
    }

    @Test
    fun `chord without a key fails`() {
        assertFailsWith<IllegalArgumentException> { registry("ALT+CTRL") }
    }

    @Test
    fun `lookup resolves registered chords and misses unknown ones`() {
        val reg = registry("ALT+V", "CTRL+S")
        assertEquals("a0", reg.forChord("alt+v")?.actionId)
        assertEquals("a1", reg.forChord("ctrl+s")?.actionId)
        assertNull(reg.forChord("alt+x"))
    }

    @Test
    fun `groups partition all shortcuts`() {
        val reg = registry("ALT+V", "CTRL+S", "ESCAPE")
        val grouped = reg.groups()
        assertEquals(3, grouped.size)
        assertNotNull(grouped["Group 0"]?.singleOrNull())
        assertEquals(reg.shortcuts.size, grouped.values.sumOf { it.size })
    }

    @Test
    fun `every registry action id is unique per chord and lookup is total over registrations`() {
        val reg = registry("ALT+V", "CTRL+S", "ESCAPE", "SHIFT+/")
        assertTrue(reg.shortcuts.all { reg.forChord(it.chord)?.actionId == it.actionId })
        // Distinct chords map to distinct entries when registered distinctly.
        assertFalse(reg.forChord("escape")!!.actionId == reg.forChord("shift+/")!!.actionId)
    }
}
