package com.ega.terminallauncher.command

import com.ega.terminallauncher.alias.AliasManager
import com.ega.terminallauncher.alias.InMemoryAliasRepository
import com.ega.terminallauncher.model.AppEntry
import com.ega.terminallauncher.mru.InMemoryMruRepository
import com.ega.terminallauncher.state.CommandAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BuiltinCommandsTest {

    private lateinit var aliasRepo: InMemoryAliasRepository
    private lateinit var aliasManager: AliasManager
    private lateinit var mruRepo: InMemoryMruRepository
    private lateinit var systemOps: FakeSystemOperations
    private lateinit var builtinCommands: BuiltinCommands

    private val apps = listOf(
        AppEntry(packageName = "com.whatsapp", label = "WhatsApp", activityName = ".Main"),
        AppEntry(packageName = "com.android.settings", label = "Settings", activityName = ".Settings")
    )

    class FakeSystemOperations : SystemOperations {
        var batteryOutput = "[Battery: 84% | Status: Discharging | Temp: 36.5°C]"
        var flashOutput = "[Flashlight ON]"
        var volumeOutput = "[Volume: 8/15 (53%)]"

        override fun getBatteryInfo(): String = batteryOutput
        override fun toggleFlashlight(): String = flashOutput
        override fun handleVolume(args: String): String = volumeOutput
    }

    @Before
    fun setUp() {
        aliasRepo = InMemoryAliasRepository()
        aliasManager = AliasManager(aliasRepo)
        mruRepo = InMemoryMruRepository()
        systemOps = FakeSystemOperations()
        builtinCommands = BuiltinCommands(systemOps, aliasManager, mruRepo)
    }

    private fun exec(firstToken: String, args: String = ""): CommandAction {
        return builtinCommands.execute(
            firstToken = firstToken,
            args = args,
            apps = apps,
            formatChoices = { it.joinToString { a -> a.label } },
            onAwaitingSelection = { _, _ -> }
        )
    }

    @Test
    fun testClearAndCls() {
        assertTrue(exec("clear") is CommandAction.ClearLogs)
        assertTrue(exec("cls") is CommandAction.ClearLogs)
    }

    @Test
    fun testAppsAndList() {
        val r1 = exec("apps")
        assertTrue(r1 is CommandAction.Message)
        val text1 = (r1 as CommandAction.Message).text
        assertTrue(text1.contains("Installed Applications (2):"))
        assertTrue(text1.contains("WhatsApp"))
        assertTrue(text1.contains("Settings"))

        val r2 = exec("list")
        assertTrue(r2 is CommandAction.Message)
        assertTrue((r2 as CommandAction.Message).text.contains("Installed Applications (2):"))
    }

    @Test
    fun testBatteryAndBat() {
        val r1 = exec("battery")
        assertTrue(r1 is CommandAction.Message)
        assertEquals(systemOps.batteryOutput, (r1 as CommandAction.Message).text)

        val r2 = exec("bat")
        assertTrue(r2 is CommandAction.Message)
        assertEquals(systemOps.batteryOutput, (r2 as CommandAction.Message).text)
    }

    @Test
    fun testFlash() {
        val r = exec("flash")
        assertTrue(r is CommandAction.Message)
        assertEquals(systemOps.flashOutput, (r as CommandAction.Message).text)
    }

    @Test
    fun testVolumeAndVol() {
        val r1 = exec("vol")
        assertTrue(r1 is CommandAction.Message)
        assertEquals(systemOps.volumeOutput, (r1 as CommandAction.Message).text)

        val r2 = exec("volume", "8")
        assertTrue(r2 is CommandAction.Message)
        assertEquals(systemOps.volumeOutput, (r2 as CommandAction.Message).text)
    }

    @Test
    fun testCalc() {
        val rEmpty = exec("calc", "")
        assertTrue(rEmpty is CommandAction.Message)
        assertEquals("[Usage: calc <expression>]", (rEmpty as CommandAction.Message).text)

        val rSuccess = exec("calc", "10 + 5 * 2")
        assertTrue(rSuccess is CommandAction.Message)
        assertEquals("20", (rSuccess as CommandAction.Message).text)

        val rDivZero = exec("calc", "1 / 0")
        assertTrue(rDivZero is CommandAction.Message)
        assertEquals("[Error: Division by zero]", (rDivZero as CommandAction.Message).text)
    }

    @Test
    fun testAliasAndUnalias() {
        val rRegister = exec("alias", "wa=WhatsApp")
        assertTrue(rRegister is CommandAction.Message)
        assertEquals("[Alias registered: wa -> WhatsApp]", (rRegister as CommandAction.Message).text)

        val rList = exec("alias", "")
        assertTrue(rList is CommandAction.Message)
        assertTrue((rList as CommandAction.Message).text.contains("wa = WhatsApp"))

        val rUnalias = exec("unalias", "wa")
        assertTrue(rUnalias is CommandAction.Message)
        assertEquals("[Alias removed: wa]", (rUnalias as CommandAction.Message).text)
    }

    @Test
    fun testResetMru() {
        mruRepo.put("wa", "com.whatsapp")
        assertEquals("com.whatsapp", mruRepo.get("wa"))

        val r = exec("reset", "mru")
        assertTrue(r is CommandAction.Message)
        assertEquals("[MRU cache cleared]", (r as CommandAction.Message).text)
        assertEquals(null, mruRepo.get("wa"))

        val rInvalid = exec("reset", "something_else")
        assertTrue(rInvalid is CommandAction.Message)
        assertEquals("[Usage: reset mru]", (rInvalid as CommandAction.Message).text)
    }

    @Test
    fun testHelp() {
        val r = exec("help")
        assertTrue(r is CommandAction.Message)
        val text = (r as CommandAction.Message).text
        assertTrue(text.contains("Ash Launcher v0.3.0 — Help"))
        assertTrue(text.contains("Built-in Commands:"))
        assertTrue(text.contains("Shell Commands (Termux IPC):"))
    }

    @Test
    fun testOpenApp() {
        val rEmpty = exec("open", "")
        assertTrue(rEmpty is CommandAction.Message)
        assertEquals("[Usage: open <app_name>]", (rEmpty as CommandAction.Message).text)

        val rMatch = exec("open", "WhatsApp")
        assertTrue(rMatch is CommandAction.Launch)
        assertEquals("WhatsApp", (rMatch as CommandAction.Launch).app.label)

        val rNoMatch = exec("open", "NonExistentApp")
        assertTrue(rNoMatch is CommandAction.Message)
        assertEquals("No match for 'NonExistentApp'", (rNoMatch as CommandAction.Message).text)
    }
}
