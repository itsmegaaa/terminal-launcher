package com.ega.terminallauncher.command

import com.ega.terminallauncher.alias.AliasManager
import com.ega.terminallauncher.alias.AliasRepository
import com.ega.terminallauncher.matcher.AppMatcher
import com.ega.terminallauncher.matcher.MatchResult
import com.ega.terminallauncher.model.AppEntry
import com.ega.terminallauncher.mru.MruRepository
import com.ega.terminallauncher.state.CommandAction

class CommandParser(
    private val builtinCommands: BuiltinCommands,
    private val aliasManager: AliasManager,
    private val aliasRepo: AliasRepository,
    private val mruRepo: MruRepository
) {
    companion object {
        val TIMEOUT_OVERRIDE_REGEX = Regex("""^\$\s*t(\d+)\s+(.+)$""")
        val GENERAL_SHELL_REGEX = Regex("""^\$\s*(.+)$""")
    }

    fun parse(
        rawInput: String,
        apps: List<AppEntry>,
        formatChoices: (List<AppEntry>) -> String,
        onAwaitingSelection: (query: String, candidates: List<AppEntry>) -> Unit,
        allowAliasExpansion: Boolean = true
    ): CommandAction {
        val input = rawInput.trim()
        if (input.isEmpty()) return CommandAction.None

        // Tier 1: Shell Execution Check ($)
        if (input.startsWith("$")) {
            val timeoutMatch = TIMEOUT_OVERRIDE_REGEX.matchEntire(input)
            if (timeoutMatch != null) {
                val timeoutSec = timeoutMatch.groupValues[1].toIntOrNull()
                val shellCmd = timeoutMatch.groupValues[2].trim()

                if (timeoutSec == null || timeoutSec !in 1..600) {
                    return CommandAction.Message("[Error: Timeout must be between 1 and 600 seconds]")
                }
                return CommandAction.ExecuteShell(
                    rawCommand = input,
                    shellCommand = shellCmd,
                    timeoutSec = timeoutSec
                )
            }

            val generalMatch = GENERAL_SHELL_REGEX.matchEntire(input)
            if (generalMatch != null) {
                val shellCmd = generalMatch.groupValues[1].trim()
                if (shellCmd.isEmpty()) {
                    return CommandAction.None
                }
                return CommandAction.ExecuteShell(
                    rawCommand = input,
                    shellCommand = shellCmd,
                    timeoutSec = 30
                )
            }

            // Bare '$' or whitespace after '$'
            return CommandAction.None
        }

        val parts = input.split(Regex("""\s+"""), limit = 2)
        val firstToken = parts[0]
        val args = if (parts.size > 1) parts[1].trim() else ""

        // Tier 2: Built-in Commands Check
        if (builtinCommands.isBuiltin(firstToken)) {
            return builtinCommands.execute(firstToken, args, apps, formatChoices, onAwaitingSelection)
        }

        // Tier 3: User Alias Expansion
        if (allowAliasExpansion) {
            val aliasTarget = aliasRepo.get(firstToken.lowercase())
            if (aliasTarget != null) {
                val expanded = aliasManager.expandAlias(input)
                if (expanded != input) {
                    return parse(
                        rawInput = expanded,
                        apps = apps,
                        formatChoices = formatChoices,
                        onAwaitingSelection = onAwaitingSelection,
                        allowAliasExpansion = false
                    )
                }
            }
        }

        // Tier 4: App Match via 3-tier AppMatcher
        val forceList = input.endsWith("?")
        val cleanQuery = input.removeSuffix("?").trim()
        if (cleanQuery.isEmpty()) {
            return CommandAction.None
        }

        return when (val matchResult = AppMatcher.match(cleanQuery, apps)) {
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
                onAwaitingSelection(cleanQuery, matchResult.candidates)
                CommandAction.ShowChoices(formatChoices(matchResult.candidates))
            }
            is MatchResult.NoMatch -> {
                CommandAction.Message("No match for '$cleanQuery'")
            }
        }
    }
}
