package com.ega.terminallauncher.command

import android.content.Context
import com.ega.terminallauncher.alias.AliasManager
import com.ega.terminallauncher.alias.AliasResult
import com.ega.terminallauncher.calc.CalcEngine
import com.ega.terminallauncher.calc.CalcResult
import com.ega.terminallauncher.matcher.AppMatcher
import com.ega.terminallauncher.matcher.MatchResult
import com.ega.terminallauncher.model.AppEntry
import com.ega.terminallauncher.mru.MruRepository
import com.ega.terminallauncher.state.CommandAction
import com.ega.terminallauncher.util.BatteryHelper
import com.ega.terminallauncher.util.FlashlightHelper
import com.ega.terminallauncher.util.VolumeHelper

interface SystemOperations {
    fun getBatteryInfo(): String
    fun toggleFlashlight(): String
    fun handleVolume(args: String): String
}

class AndroidSystemOperations(context: Context) : SystemOperations {
    private val appContext = context.applicationContext
    private val flashlightHelper = FlashlightHelper(appContext)

    override fun getBatteryInfo(): String = BatteryHelper.getBatteryInfo(appContext)
    override fun toggleFlashlight(): String = flashlightHelper.toggle()
    override fun handleVolume(args: String): String = VolumeHelper.handleVolume(appContext, args)
}

class BuiltinCommands(
    private val systemOps: SystemOperations,
    private val aliasManager: AliasManager,
    private val mruRepo: MruRepository
) {
    fun isBuiltin(firstToken: String): Boolean {
        return AliasManager.RESERVED_WORDS.contains(firstToken.lowercase())
    }

    fun execute(
        firstToken: String,
        args: String,
        apps: List<AppEntry>,
        formatChoices: (List<AppEntry>) -> String,
        onAwaitingSelection: (query: String, candidates: List<AppEntry>) -> Unit
    ): CommandAction {
        return when (firstToken.lowercase()) {
            "clear", "cls" -> CommandAction.ClearLogs

            "apps", "list" -> {
                if (apps.isEmpty()) {
                    CommandAction.Message("[No installed apps found]")
                } else {
                    val count = apps.size
                    val sb = StringBuilder("Installed Applications ($count):\n")
                    apps.sortedBy { it.label.lowercase() }.forEachIndexed { idx, app ->
                        sb.append("  ${app.label}")
                        if (idx < count - 1) sb.append("\n")
                    }
                    CommandAction.Message(sb.toString())
                }
            }

            "battery", "bat" -> {
                CommandAction.Message(systemOps.getBatteryInfo())
            }

            "flash" -> {
                CommandAction.Message(systemOps.toggleFlashlight())
            }

            "vol", "volume" -> {
                CommandAction.Message(systemOps.handleVolume(args))
            }

            "calc" -> {
                if (args.trim().isEmpty()) {
                    CommandAction.Message("[Usage: calc <expression>]")
                } else {
                    when (val res = CalcEngine.evaluate(args)) {
                        is CalcResult.Success -> CommandAction.Message(res.formatted)
                        is CalcResult.Error -> CommandAction.Message(res.message)
                    }
                }
            }

            "alias" -> {
                when (val res = aliasManager.handleAliasCommand(args)) {
                    is AliasResult.Success -> CommandAction.Message(res.message)
                    is AliasResult.Error -> CommandAction.Message(res.message)
                }
            }

            "unalias" -> {
                when (val res = aliasManager.handleUnaliasCommand(args)) {
                    is AliasResult.Success -> CommandAction.Message(res.message)
                    is AliasResult.Error -> CommandAction.Message(res.message)
                }
            }

            "reset" -> {
                if (args.trim().equals("mru", ignoreCase = true)) {
                    mruRepo.clear()
                    CommandAction.Message("[MRU cache cleared]")
                } else {
                    CommandAction.Message("[Usage: reset mru]")
                }
            }

            "help" -> {
                val helpText = """
                Ash Launcher v0.3.0 — Help
                ==========================
                Built-in Commands:
                  help                Show this help table
                  clear, cls          Clear terminal log
                  apps, list          List installed applications
                  battery, bat        Display battery status & temp
                  flash               Toggle flashlight on/off
                  vol, volume [level] View or set media volume (0-100% or 0-max)
                  calc <expr>         Inline arithmetic calculator (+ - * / % ^ ())
                  alias [name=cmd]    View or register custom aliases
                  unalias <name>      Remove custom alias
                  reset mru           Clear MRU app launch history
                  open <app>          Launch app directly (bypassing reserved words)

                Shell Commands (Termux IPC):
                  ${'$'} <cmd>             Run shell command (default 30s timeout)
                  ${'$'}t<sec> <cmd>       Run shell command with custom timeout (1-600s)
                  e.g. ${'$'}ls, ${'$'}t10 ping -c 3 1.1.1.1
                """.trimIndent()
                CommandAction.Message(helpText)
            }

            "open" -> {
                val query = args.trim()
                if (query.isEmpty()) {
                    CommandAction.Message("[Usage: open <app_name>]")
                } else {
                    when (val matchResult = AppMatcher.match(query, apps)) {
                        is MatchResult.Exact -> CommandAction.Launch(matchResult.app, source = "exact")
                        is MatchResult.Single -> CommandAction.Launch(matchResult.app, source = "single")
                        is MatchResult.Ambiguous -> {
                            val rememberedPkg = mruRepo.get(query)
                            val rememberedApp = matchResult.candidates.firstOrNull { it.packageName == rememberedPkg }
                            if (rememberedApp != null) {
                                CommandAction.Launch(rememberedApp, source = "mru")
                            } else {
                                onAwaitingSelection(query, matchResult.candidates)
                                CommandAction.ShowChoices(formatChoices(matchResult.candidates))
                            }
                        }
                        is MatchResult.NoMatch -> CommandAction.Message("No match for '$query'")
                    }
                }
            }

            else -> CommandAction.Message("[Error: Unknown command '$firstToken']")
        }
    }
}
