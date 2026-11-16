package com.ega.terminallauncher.command

import com.ega.terminallauncher.alias.AliasManager
import com.ega.terminallauncher.alias.AliasResult
import com.ega.terminallauncher.alias.InMemoryAliasRepository
import com.ega.terminallauncher.model.AppEntry
import com.ega.terminallauncher.mru.InMemoryMruRepository
import com.ega.terminallauncher.state.CommandAction
import com.ega.terminallauncher.ui.theme.InMemoryThemeRepository
import com.ega.terminallauncher.ui.theme.ThemePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ThemeCommandTest {

    private lateinit var themeRepo: InMemoryThemeRepository
    private lateinit var aliasRepo: InMemoryAliasRepository
    private lateinit var aliasManager: AliasManager
    private lateinit var mruRepo: InMemoryMruRepository
    private lateinit var builtinCommands: BuiltinCommands
    private val apps = emptyList<AppEntry>()

    private class FakeSystemOperations : SystemOperations {
        override fun getBatteryInfo(): String = "[Battery: 100%]"
        override fun toggleFlashlight(): String = "[Flashlight ON]"
        override fun handleVolume(args: String): String = "[Volume: 10/15]"
    }

    @Before
    fun setUp() {
        themeRepo = InMemoryThemeRepository()
        aliasRepo = InMemoryAliasRepository()
        aliasManager = AliasManager(aliasRepo)
        mruRepo = InMemoryMruRepository()
        builtinCommands = BuiltinCommands(FakeSystemOperations(), aliasManager, mruRepo, themeRepo)
    }

    private fun exec(args: String = ""): CommandAction {
        return builtinCommands.execute(
            firstToken = "theme",
            args = args,
            apps = apps,
            formatChoices = { "" },
            onAwaitingSelection = { _, _ -> }
        )
    }

    @Test
    fun testThemeNoArgsDisplaysCurrentAndAvailableThemes() {
        val result = exec()
        assertTrue(result is CommandAction.Message)
        val text = (result as CommandAction.Message).text
        assertTrue(text.contains("Active theme: claude"))
        assertTrue(text.contains("claude, cyberpunk, matrix, amber, monochrome"))
    }

    @Test
    fun testThemeValidPresetSwitchesAndPersists() {
        val result = exec("cyberpunk")
        assertTrue(result is CommandAction.SetTheme)
        val setTheme = result as CommandAction.SetTheme
        assertEquals(ThemePreset.CYBERPUNK, setTheme.theme)
        assertEquals("[Theme set to 'cyberpunk']", setTheme.message)
        assertEquals(ThemePreset.CYBERPUNK, themeRepo.getTheme())

        val resultMatrix = exec("MATRIX")
        assertTrue(resultMatrix is CommandAction.SetTheme)
        assertEquals(ThemePreset.MATRIX, (resultMatrix as CommandAction.SetTheme).theme)
        assertEquals(ThemePreset.MATRIX, themeRepo.getTheme())
    }

    @Test
    fun testThemeInvalidPresetReturnsError() {
        val result = exec("unknown")
        assertTrue(result is CommandAction.Message)
        val text = (result as CommandAction.Message).text
        assertTrue(text.contains("[Error: Unknown theme 'unknown'."))
        assertTrue(text.contains("Available: claude, cyberpunk, matrix, amber, monochrome]"))
        assertEquals(ThemePreset.CLAUDE, themeRepo.getTheme())
    }

    @Test
    fun testThemeIsReservedWord() {
        assertTrue(AliasManager.RESERVED_WORDS.contains("theme"))
        val result = aliasManager.handleAliasCommand("theme=clear")
        assertTrue(result is AliasResult.Error)
        assertEquals("[Error: 'theme' is a reserved command name]", (result as AliasResult.Error).message)
    }

    @Test
    fun testThemeParsedByCommandParser() {
        val parser = CommandParser(builtinCommands, aliasManager, aliasRepo, mruRepo)
        val action = parser.parse("theme amber", apps, { "" }, { _, _ -> })
        assertTrue(action is CommandAction.SetTheme)
        assertEquals(ThemePreset.AMBER, (action as CommandAction.SetTheme).theme)
    }
}
