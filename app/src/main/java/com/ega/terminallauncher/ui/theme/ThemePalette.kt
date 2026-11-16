package com.ega.terminallauncher.ui.theme

import androidx.compose.ui.graphics.Color

enum class ThemePreset(val id: String, val displayName: String) {
    CLAUDE("claude", "Claude Code"),
    CYBERPUNK("cyberpunk", "Cyberpunk Neon"),
    MATRIX("matrix", "Matrix Phosphor"),
    AMBER("amber", "Retro CRT Amber"),
    MONOCHROME("monochrome", "High-Contrast Monochrome");

    companion object {
        fun fromId(id: String): ThemePreset? = entries.firstOrNull { it.id.equals(id.trim(), ignoreCase = true) }
        fun fromString(str: String): ThemePreset? = fromId(str)
    }
}

data class ThemePalette(
    val id: String,
    val background: Color,
    val surface: Color,
    val primary: Color,
    val secondary: Color,
    val text: Color,
    val textMuted: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val cursor: Color
)

object ThemePresets {
    val CLAUDE = ThemePalette(
        id = "claude",
        background = Color(0xFF141413),
        surface = Color(0xFF1A1918),
        primary = Color(0xFFD97757),
        secondary = Color(0xFF87867F),
        text = Color(0xFFFAF9F5),
        textMuted = Color(0xFF87867F),
        success = Color(0xFF788F6A),
        warning = Color(0xFFD4A359),
        error = Color(0xFFC45C50),
        cursor = Color(0xFFD97757)
    )

    val CYBERPUNK = ThemePalette(
        id = "cyberpunk",
        background = Color(0xFF0D0D17),
        surface = Color(0xFF161629),
        primary = Color(0xFF00F0FF),
        secondary = Color(0xFFFF007F),
        text = Color(0xFFE0F7FA),
        textMuted = Color(0xFF707090),
        success = Color(0xFF00FF9F),
        warning = Color(0xFFFFEE55),
        error = Color(0xFFFF0055),
        cursor = Color(0xFF00F0FF)
    )

    val MATRIX = ThemePalette(
        id = "matrix",
        background = Color(0xFF000000),
        surface = Color(0xFF0A140A),
        primary = Color(0xFF00FF41),
        secondary = Color(0xFF008F11),
        text = Color(0xFF00FF66),
        textMuted = Color(0xFF005511),
        success = Color(0xFF00FF41),
        warning = Color(0xFFAAFF00),
        error = Color(0xFFFF3333),
        cursor = Color(0xFF00FF41)
    )

    val AMBER = ThemePalette(
        id = "amber",
        background = Color(0xFF120C02),
        surface = Color(0xFF1F1505),
        primary = Color(0xFFFFB000),
        secondary = Color(0xFFFF8C00),
        text = Color(0xFFFFC83B),
        textMuted = Color(0xFF8C6214),
        success = Color(0xFFFFD700),
        warning = Color(0xFFFF9900),
        error = Color(0xFFFF3300),
        cursor = Color(0xFFFFB000)
    )

    val MONOCHROME = ThemePalette(
        id = "monochrome",
        background = Color(0xFF000000),
        surface = Color(0xFF121212),
        primary = Color(0xFFFFFFFF),
        secondary = Color(0xFFAAAAAA),
        text = Color(0xFFFFFFFF),
        textMuted = Color(0xFF666666),
        success = Color(0xFFCCCCCC),
        warning = Color(0xFF888888),
        error = Color(0xFFFFFFFF),
        cursor = Color(0xFFFFFFFF)
    )

    fun get(preset: ThemePreset): ThemePalette = when (preset) {
        ThemePreset.CLAUDE -> CLAUDE
        ThemePreset.CYBERPUNK -> CYBERPUNK
        ThemePreset.MATRIX -> MATRIX
        ThemePreset.AMBER -> AMBER
        ThemePreset.MONOCHROME -> MONOCHROME
    }
}
