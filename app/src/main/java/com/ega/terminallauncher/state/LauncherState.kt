package com.ega.terminallauncher.state

import com.ega.terminallauncher.alias.AliasManager
import com.ega.terminallauncher.alias.AliasRepository
import com.ega.terminallauncher.alias.InMemoryAliasRepository
import com.ega.terminallauncher.command.BuiltinCommands
import com.ega.terminallauncher.command.CommandParser
import com.ega.terminallauncher.command.SystemOperations
import com.ega.terminallauncher.model.AppEntry
import com.ega.terminallauncher.mru.MruRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface LauncherState {
    object Normal : LauncherState
    data class AwaitingSelection(val query: String, val candidates: List<AppEntry>) : LauncherState
}

sealed interface CommandAction {
    data class Launch(val app: AppEntry, val source: String) : CommandAction
    data class ShowChoices(val choicesText: String) : CommandAction
    data class Message(val text: String) : CommandAction
    data class ExecuteShell(val rawCommand: String, val shellCommand: String, val timeoutSec: Int) : CommandAction
    data object ClearLogs : CommandAction
    data object None : CommandAction
}

class LauncherStateMachine(
    val mruRepo: MruRepository,
    commandParser: CommandParser? = null,
    val aliasRepo: AliasRepository = InMemoryAliasRepository(),
    private val scope: CoroutineScope? = null,
    private val onStateChanged: ((LauncherState) -> Unit)? = null
) {
    val aliasManager: AliasManager = AliasManager(aliasRepo)
    val commandParser: CommandParser = commandParser ?: run {
        val noopSystemOps = object : SystemOperations {
            override fun getBatteryInfo(): String = "[Battery: 100% | Status: Full | Temp: 25.0°C]"
            override fun toggleFlashlight(): String = "[Flashlight ON]"
            override fun handleVolume(args: String): String = "[Volume: 10/15 (66%)]"
        }
        val builtin = BuiltinCommands(noopSystemOps, aliasManager, mruRepo)
        CommandParser(builtin, aliasManager, aliasRepo, mruRepo)
    }

    var state: LauncherState = LauncherState.Normal
        private set(value) {
            field = value
            onStateChanged?.invoke(value)
        }

    private var timeoutJob: Job? = null

    fun onInput(rawInput: String, apps: List<AppEntry>): CommandAction {
        val input = rawInput.trim()
        if (input.isEmpty()) return CommandAction.None

        val currentState = state
        if (currentState is LauncherState.AwaitingSelection) {
            cancelTimeout()
            val selectedIndex = input.toIntOrNull()
            if (selectedIndex != null && selectedIndex in 1..currentState.candidates.size) {
                val selectedApp = currentState.candidates[selectedIndex - 1]
                mruRepo.put(currentState.query, selectedApp.packageName)
                state = LauncherState.Normal
                return CommandAction.Launch(selectedApp, source = "selection")
            } else {
                // Non-numeric or out-of-range cancels selection and falls through to normal execution
                state = LauncherState.Normal
            }
        }

        return commandParser.parse(
            rawInput = input,
            apps = apps,
            formatChoices = ::formatChoices,
            onAwaitingSelection = { query, candidates ->
                state = LauncherState.AwaitingSelection(query, candidates)
                startTimeout()
            }
        )
    }

    fun onBack(): Boolean {
        if (state is LauncherState.AwaitingSelection) {
            cancelTimeout()
            state = LauncherState.Normal
            return true
        }
        return false
    }

    fun onNewIntent() {
        cancelTimeout()
        state = LauncherState.Normal
    }

    fun expire() {
        cancelTimeout()
        state = LauncherState.Normal
    }

    private fun startTimeout() {
        cancelTimeout()
        scope?.let {
            timeoutJob = it.launch {
                delay(15_000L)
                state = LauncherState.Normal
            }
        }
    }

    private fun cancelTimeout() {
        timeoutJob?.cancel()
        timeoutJob = null
    }

    companion object {
        fun formatChoices(candidates: List<AppEntry>): String {
            return candidates.mapIndexed { index, app ->
                "[${index + 1}] ${app.label}"
            }.joinToString("  ")
        }
    }
}
