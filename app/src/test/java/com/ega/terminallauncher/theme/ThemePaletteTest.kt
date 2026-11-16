package com.ega.terminallauncher.theme

import androidx.compose.ui.graphics.Color
import com.ega.terminallauncher.ui.theme.InMemoryThemeRepository
import com.ega.terminallauncher.ui.theme.ThemePalette
import com.ega.terminallauncher.ui.theme.ThemePreset
import com.ega.terminallauncher.ui.theme.ThemePresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePaletteTest {

    @Test
    fun testAllFivePresetsExist() {
        val presets = ThemePreset.entries
        assertEquals(5, presets.size)
        assertTrue(presets.any { it.id == "claude" })
        assertTrue(presets.any { it.id == "cyberpunk" })
        assertTrue(presets.any { it.id == "matrix" })
        assertTrue(presets.any { it.id == "amber" })
        assertTrue(presets.any { it.id == "monochrome" })
    }

    @Test
    fun testPresetFromIdCaseInsensitive() {
        assertEquals(ThemePreset.CLAUDE, ThemePreset.fromId("claude"))
        assertEquals(ThemePreset.CLAUDE, ThemePreset.fromId("CLAUDE"))
        assertEquals(ThemePreset.CYBERPUNK, ThemePreset.fromId("  cyberpunk  "))
        assertEquals(ThemePreset.MATRIX, ThemePreset.fromString("Matrix"))
        assertEquals(ThemePreset.AMBER, ThemePreset.fromId("amber"))
        assertEquals(ThemePreset.MONOCHROME, ThemePreset.fromId("monochrome"))
        assertNull(ThemePreset.fromId("nonexistent"))
    }

    @Test
    fun testClaudePaletteSemanticColors() {
        val palette = ThemePresets.get(ThemePreset.CLAUDE)
        assertEquals("claude", palette.id)
        assertEquals(Color(0xFF141413), palette.background)
        assertEquals(Color(0xFF1A1918), palette.surface)
        assertEquals(Color(0xFFD97757), palette.primary)
        assertEquals(Color(0xFF87867F), palette.secondary)
        assertEquals(Color(0xFFFAF9F5), palette.text)
        assertEquals(Color(0xFF87867F), palette.textMuted)
        assertEquals(Color(0xFF788F6A), palette.success)
        assertEquals(Color(0xFFD4A359), palette.warning)
        assertEquals(Color(0xFFC45C50), palette.error)
        assertEquals(Color(0xFFD97757), palette.cursor)
    }

    @Test
    fun testAllPresetPalettesNotNullAndDistinct() {
        val palettes = ThemePreset.entries.map { ThemePresets.get(it) }
        assertEquals(5, palettes.size)
        palettes.forEach { palette ->
            assertNotNull(palette.id)
            assertNotNull(palette.background)
            assertNotNull(palette.primary)
            assertNotNull(palette.text)
        }
    }

    @Test
    fun testInMemoryThemeRepository() {
        val repo = InMemoryThemeRepository()
        assertEquals(ThemePreset.CLAUDE, repo.getTheme())

        repo.setTheme(ThemePreset.CYBERPUNK)
        assertEquals(ThemePreset.CYBERPUNK, repo.getTheme())

        repo.setTheme(ThemePreset.MATRIX)
        assertEquals(ThemePreset.MATRIX, repo.getTheme())
    }
}
