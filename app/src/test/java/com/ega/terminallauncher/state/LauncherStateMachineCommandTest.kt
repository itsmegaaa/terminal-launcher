package com.ega.terminallauncher.state

import com.ega.terminallauncher.alias.AliasManager
import com.ega.terminallauncher.alias.InMemoryAliasRepository
import com.ega.terminallauncher.command.BuiltinCommands
import com.ega.terminallauncher.command.CommandParser
import com.ega.terminallauncher.command.SystemOperations
import com.ega.terminallauncher.model.AppEntry
import com.ega.terminallauncher.mru.InMemoryMruRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LauncherStateMachineCommandTest {

    private lateinit var mruRepo: InMemoryMruRepository
    private lateinit var aliasRepo: InMemoryAliasRepository
    private lateinit var aliasManager: AliasManager
    private lateinit var systemOps: FakeSystemOps
    private lateinit var builtinCommands: BuiltinCommands
    private lateinit var commandParser: CommandParser
    private lateinit var stateMachine: LauncherStateMachine

    private val apps = listOf(
        AppEntry(packageName = "com.whatsapp", label = "WhatsApp", activityName = ".Main"),
        AppEntry(packageName = "com.whatsapp.w4b", label = "WhatsApp Business", activityName = ".W4B"),
        AppEntry(packageName = "com.google.android.calculator", label = "Calculator", activityName = ".Calc"),
        AppEntry(packageName = "com.android.settings", label = "Settings", activityName = ".Settings")
    )

    class FakeSystemOps : SystemOperations {
        override fun getBatteryInfo(): String = "[Battery: 90% | Status: Charging | Temp: 28.0°C]"
        override fun toggleFlashlight(): String = "[Flashlight ON]"
        override fun handleVolume(args: String): String = "[Volume: 5/15 (33%)]"
    }

    @Before
    fun setUp() {
        mruRepo = InMemoryMruRepository()
        aliasRepo = InMemoryAliasRepository()
        aliasManager = AliasManager(aliasRepo)
        systemOps = FakeSystemOps()
        builtinCommands = BuiltinCommands(systemOps, aliasManager, mruRepo)
        commandParser = CommandParser(builtinCommands, aliasManager, aliasRepo, mruRepo)
        stateMachine = LauncherStateMachine(
            mruRepo = mruRepo,
            commandParser = commandParser,
            aliasRepo = aliasRepo
        )
    }

    @Test
    fun testAmbiguousQueryEntersAwaitingSelection() {
        val action = stateMachine.onInput("Whats", apps)
        assertTrue(action is CommandAction.ShowChoices)
        assertTrue(stateMachine.state is LauncherState.AwaitingSelection)

        val awaiting = stateMachine.state as LauncherState.AwaitingSelection
        assertEquals(2, awaiting.candidates.size)
    }

    @Test
    fun testNumericSelectionInAwaitingSelection() {
        stateMachine.onInput("Whats", apps)
        assertTrue(stateMachine.state is LauncherState.AwaitingSelection)

        // Select 2
        val action = stateMachine.onInput("2", apps)
        assertTrue(action is CommandAction.Launch)
        val launched = action as CommandAction.Launch
        assertEquals("WhatsApp Business", launched.app.label)
        assertEquals("selection", launched.source)
        assertTrue(stateMachine.state is LauncherState.Normal)

        // MRU should be recorded
        assertEquals("com.whatsapp.w4b", mruRepo.get("Whats"))
    }

    @Test
    fun testNonNumericPreemptionInAwaitingSelectionWithShellCommand() {
        stateMachine.onInput("Whats", apps)
        assertTrue(stateMachine.state is LauncherState.AwaitingSelection)

        // Non-numeric shell command $ls cancels selection and executes shell command immediately
        val action = stateMachine.onInput("\$ls", apps)
        assertTrue(action is CommandAction.ExecuteShell)
        assertEquals("ls", (action as CommandAction.ExecuteShell).shellCommand)
        assertTrue(stateMachine.state is LauncherState.Normal)
    }

    @Test
    fun testNonNumericPreemptionInAwaitingSelectionWithBuiltinCommand() {
        stateMachine.onInput("Whats", apps)
        assertTrue(stateMachine.state is LauncherState.AwaitingSelection)

        // Non-numeric built-in 'clear' cancels selection and executes ClearLogs
        val action = stateMachine.onInput("clear", apps)
        assertTrue(action is CommandAction.ClearLogs)
        assertTrue(stateMachine.state is LauncherState.Normal)
    }

    @Test
    fun testNonNumericPreemptionInAwaitingSelectionWithAnotherAppQuery() {
        stateMachine.onInput("Whats", apps)
        assertTrue(stateMachine.state is LauncherState.AwaitingSelection)

        // Querying "Settings" cancels previous selection and launches Settings
        val action = stateMachine.onInput("Settings", apps)
        assertTrue(action is CommandAction.Launch)
        assertEquals("Settings", (action as CommandAction.Launch).app.label)
        assertTrue(stateMachine.state is LauncherState.Normal)
    }

    @Test
    fun testOnBackCancelsSelection() {
        stateMachine.onInput("Whats", apps)
        assertTrue(stateMachine.state is LauncherState.AwaitingSelection)

        val handled = stateMachine.onBack()
        assertTrue(handled)
        assertTrue(stateMachine.state is LauncherState.Normal)

        val handledAgain = stateMachine.onBack()
        assertFalse(handledAgain)
    }

    @Test
    fun testOnNewIntentCancelsSelection() {
        stateMachine.onInput("Whats", apps)
        assertTrue(stateMachine.state is LauncherState.AwaitingSelection)

        stateMachine.onNewIntent()
        assertTrue(stateMachine.state is LauncherState.Normal)
    }
}
