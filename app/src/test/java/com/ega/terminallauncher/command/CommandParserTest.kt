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

class CommandParserTest {

    private lateinit var aliasRepo: InMemoryAliasRepository
    private lateinit var aliasManager: AliasManager
    private lateinit var mruRepo: InMemoryMruRepository
    private lateinit var systemOps: FakeSystemOperations
    private lateinit var builtinCommands: BuiltinCommands
    private lateinit var parser: CommandParser

    private val dummyApps = listOf(
        AppEntry(packageName = "com.whatsapp", label = "WhatsApp", activityName = ".Main"),
        AppEntry(packageName = "com.google.android.calculator", label = "Calculator", activityName = ".Calc"),
        AppEntry(packageName = "com.android.settings", label = "Settings", activityName = ".Settings")
    )

    class FakeSystemOperations : SystemOperations {
        var batteryText = "[Battery: 85% | Status: Discharging | Temp: 32.0°C]"
        var flashStatus = "[Flashlight ON]"
        var volumeStatus = "[Volume: 10/15 (66%)]"

        override fun getBatteryInfo(): String = batteryText
        override fun toggleFlashlight(): String = flashStatus
        override fun handleVolume(args: String): String = volumeStatus
    }

    @Before
    fun setUp() {
        aliasRepo = InMemoryAliasRepository()
        aliasManager = AliasManager(aliasRepo)
        mruRepo = InMemoryMruRepository()
        systemOps = FakeSystemOperations()
        builtinCommands = BuiltinCommands(systemOps, aliasManager, mruRepo)
        parser = CommandParser(builtinCommands, aliasManager, aliasRepo, mruRepo)
    }

    private fun parse(input: String): CommandAction {
        return parser.parse(
            rawInput = input,
            apps = dummyApps,
            formatChoices = { it.joinToString { app -> app.label } },
            onAwaitingSelection = { _, _ -> }
        )
    }

    @Test
    fun testTimeoutOverrideValid() {
        val r1 = parse("\$t30 pkg update")
        assertTrue(r1 is CommandAction.ExecuteShell)
        val s1 = r1 as CommandAction.ExecuteShell
        assertEquals("pkg update", s1.shellCommand)
        assertEquals(30, s1.timeoutSec)

        val r2 = parse("\$   t120   ls -la")
        assertTrue(r2 is CommandAction.ExecuteShell)
        val s2 = r2 as CommandAction.ExecuteShell
        assertEquals("ls -la", s2.shellCommand)
        assertEquals(120, s2.timeoutSec)

        val r3 = parse("\$t1 sleep 1")
        assertTrue(r3 is CommandAction.ExecuteShell)
        val s3 = r3 as CommandAction.ExecuteShell
        assertEquals("sleep 1", s3.shellCommand)
        assertEquals(1, s3.timeoutSec)

        val r4 = parse("\$t600 ./long_script.sh")
        assertTrue(r4 is CommandAction.ExecuteShell)
        val s4 = r4 as CommandAction.ExecuteShell
        assertEquals("./long_script.sh", s4.shellCommand)
        assertEquals(600, s4.timeoutSec)
    }

    @Test
    fun testTimeoutOverrideOutOfBounds() {
        val r0 = parse("\$t0 ls")
        assertTrue(r0 is CommandAction.Message)
        assertEquals("[Error: Timeout must be between 1 and 600 seconds]", (r0 as CommandAction.Message).text)

        val r601 = parse("\$t601 ls")
        assertTrue(r601 is CommandAction.Message)
        assertEquals("[Error: Timeout must be between 1 and 600 seconds]", (r601 as CommandAction.Message).text)

        val r9999 = parse("\$t9999 pkg update")
        assertTrue(r9999 is CommandAction.Message)
        assertEquals("[Error: Timeout must be between 1 and 600 seconds]", (r9999 as CommandAction.Message).text)
    }

    @Test
    fun testAntiCollisionCommandsStartingWithT() {
        val rTop = parse("\$top")
        assertTrue(rTop is CommandAction.ExecuteShell)
        val sTop = rTop as CommandAction.ExecuteShell
        assertEquals("top", sTop.shellCommand)
        assertEquals(30, sTop.timeoutSec)

        val rTar = parse("\$tar -xf archive.tar")
        assertTrue(rTar is CommandAction.ExecuteShell)
        val sTar = rTar as CommandAction.ExecuteShell
        assertEquals("tar -xf archive.tar", sTar.shellCommand)
        assertEquals(30, sTar.timeoutSec)

        val rTouch = parse("\$touch filename.txt")
        assertTrue(rTouch is CommandAction.ExecuteShell)
        val sTouch = rTouch as CommandAction.ExecuteShell
        assertEquals("touch filename.txt", sTouch.shellCommand)
        assertEquals(30, sTouch.timeoutSec)

        val rTail = parse("\$tail -n 20 /sdcard/log")
        assertTrue(rTail is CommandAction.ExecuteShell)
        val sTail = rTail as CommandAction.ExecuteShell
        assertEquals("tail -n 20 /sdcard/log", sTail.shellCommand)
        assertEquals(30, sTail.timeoutSec)

        val rTee = parse("\$tee output.log")
        assertTrue(rTee is CommandAction.ExecuteShell)
        val sTee = rTee as CommandAction.ExecuteShell
        assertEquals("tee output.log", sTee.shellCommand)
        assertEquals(30, sTee.timeoutSec)

        val rBareT = parse("\$t")
        assertTrue(rBareT is CommandAction.ExecuteShell)
        val sBareT = rBareT as CommandAction.ExecuteShell
        assertEquals("t", sBareT.shellCommand)
        assertEquals(30, sBareT.timeoutSec)

        val rT30 = parse("\$t30")
        assertTrue(rT30 is CommandAction.ExecuteShell)
        val sT30 = rT30 as CommandAction.ExecuteShell
        assertEquals("t30", sT30.shellCommand)
        assertEquals(30, sT30.timeoutSec)

        val rTSpace30 = parse("\$t 30 ls")
        assertTrue(rTSpace30 is CommandAction.ExecuteShell)
        val sTSpace30 = rTSpace30 as CommandAction.ExecuteShell
        assertEquals("t 30 ls", sTSpace30.shellCommand)
        assertEquals(30, sTSpace30.timeoutSec)
    }

    @Test
    fun testGeneralShellExecution() {
        val r1 = parse("\$ls")
        assertTrue(r1 is CommandAction.ExecuteShell)
        assertEquals("ls", (r1 as CommandAction.ExecuteShell).shellCommand)
        assertEquals(30, r1.timeoutSec)

        val r2 = parse("\$ uname -a")
        assertTrue(r2 is CommandAction.ExecuteShell)
        assertEquals("uname -a", (r2 as CommandAction.ExecuteShell).shellCommand)
        assertEquals(30, r2.timeoutSec)

        val rBare = parse("\$")
        assertTrue(rBare is CommandAction.None)

        val rBareSpace = parse("\$   ")
        assertTrue(rBareSpace is CommandAction.None)
    }

    @Test
    fun testPrecedenceShellOverBuiltinAndApps() {
        // Even if command name is 'clear' or 'calc', leading '$' routes to shell
        val rClear = parse("\$clear")
        assertTrue(rClear is CommandAction.ExecuteShell)
        assertEquals("clear", (rClear as CommandAction.ExecuteShell).shellCommand)

        val rCalc = parse("\$calc")
        assertTrue(rCalc is CommandAction.ExecuteShell)
        assertEquals("calc", (rCalc as CommandAction.ExecuteShell).shellCommand)
    }

    @Test
    fun testPrecedenceBuiltinOverAliasAndApp() {
        // Builtin 'calc' evaluates calculator, not an app named Calculator
        val rCalc = parse("calc 2 + 2")
        assertTrue(rCalc is CommandAction.Message)
        assertEquals("4", (rCalc as CommandAction.Message).text)

        // Escape hatch 'open calc' evaluates app matcher
        val rOpen = parse("open calc")
        assertTrue(rOpen is CommandAction.Launch)
        assertEquals("Calculator", (rOpen as CommandAction.Launch).app.label)
    }

    @Test
    fun testPrecedenceAliasOverApp() {
        aliasManager.handleAliasCommand("set=\$settings")
        val r = parse("set")
        assertTrue(r is CommandAction.ExecuteShell)
        assertEquals("settings", (r as CommandAction.ExecuteShell).shellCommand)
    }
}
