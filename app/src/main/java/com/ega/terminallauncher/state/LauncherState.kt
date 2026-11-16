package com.ega.terminallauncher.state

import com.ega.terminallauncher.matcher.AppMatcher
import com.ega.terminallauncher.matcher.MatchResult
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
    object None : CommandAction
}

class LauncherStateMachine(
    val mruRepo: MruRepository,
    private val scope: CoroutineScope? = null,
    private val onStateChanged: ((LauncherState) -> Unit)? = null
) {
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

        if (input.equals("reset mru", ignoreCase = true)) {
            mruRepo.clear()
            return CommandAction.Message("[MRU cache cleared]")
        }

        val forceList = input.endsWith("?")
        val cleanQuery = input.removeSuffix("?").trim()
        if (cleanQuery.isEmpty()) {
            return CommandAction.None
        }

        val matchResult = AppMatcher.match(cleanQuery, apps)
        return when (matchResult) {
            is MatchResult.Exact -> {
                CommandAction.Launch(matchResult.app, source = "exact")
            }
            is MatchResult.Single -> {
                CommandAction.Launch(matchResult.app, source = "single")
            }
            is MatchResult.Ambiguous -> {
                if (!forceList) {
                    val rememberedPkg = mruRepo.get(cleanQuery)
                    val rememberedApp = matchResult.candidates.firstOrNull { it.packageName == rememberedPkg }
                    if (rememberedApp != null) {
                        return CommandAction.Launch(rememberedApp, source = "mru")
                    }
                }
                state = LauncherState.AwaitingSelection(cleanQuery, matchResult.candidates)
                startTimeout()
                val formatted = formatChoices(matchResult.candidates)
                CommandAction.ShowChoices(formatted)
            }
            is MatchResult.NoMatch -> {
                CommandAction.Message("No match for '$cleanQuery'")
            }
        }
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
