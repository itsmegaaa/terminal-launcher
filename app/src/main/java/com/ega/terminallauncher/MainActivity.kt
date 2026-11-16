package com.ega.terminallauncher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.ega.terminallauncher.alias.AliasManager
import com.ega.terminallauncher.alias.AliasRepository
import com.ega.terminallauncher.alias.SharedPrefsAliasRepository
import com.ega.terminallauncher.apps.AppManager
import com.ega.terminallauncher.autocomplete.AutoCompleteEngine
import com.ega.terminallauncher.autocomplete.DefaultAutoCompleteEngine
import com.ega.terminallauncher.bridge.TermuxBridge
import com.ega.terminallauncher.command.AndroidSystemOperations
import com.ega.terminallauncher.command.BuiltinCommands
import com.ega.terminallauncher.command.CommandParser
import com.ega.terminallauncher.command.SystemOperations
import com.ega.terminallauncher.history.HistoryBuffer
import com.ega.terminallauncher.history.InMemoryHistoryBuffer
import com.ega.terminallauncher.mru.SharedPrefsMruRepository
import com.ega.terminallauncher.state.CommandAction
import com.ega.terminallauncher.state.LauncherState
import com.ega.terminallauncher.state.LauncherStateMachine
import com.ega.terminallauncher.ui.components.PromptFooter
import com.ega.terminallauncher.ui.components.QuickActionToolbar
import com.ega.terminallauncher.ui.components.TerminalLogViewer
import com.ega.terminallauncher.ui.components.TopHeader
import com.ega.terminallauncher.ui.theme.SharedPrefsThemeRepository
import com.ega.terminallauncher.ui.theme.ThemePresets
import com.ega.terminallauncher.ui.theme.ThemeRepository
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private lateinit var bridge: TermuxBridge
    private lateinit var mruRepo: SharedPrefsMruRepository
    private lateinit var appManager: AppManager
    private lateinit var stateMachine: LauncherStateMachine
    private lateinit var themeRepo: ThemeRepository
    private lateinit var systemOps: SystemOperations
    private lateinit var aliasRepo: AliasRepository

    private val reFocusTrigger = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            android.util.Log.e("AshLauncher", "FATAL CRASH", throwable)
        }

        bridge = TermuxBridge(this)
        mruRepo = SharedPrefsMruRepository(this)
        appManager = AppManager(this, mruRepo)
        themeRepo = SharedPrefsThemeRepository(this)
        aliasRepo = SharedPrefsAliasRepository(this)
        val aliasManager = AliasManager(aliasRepo)
        systemOps = AndroidSystemOperations(this)
        val builtinCommands = BuiltinCommands(systemOps, aliasManager, mruRepo, themeRepo)
        val commandParser = CommandParser(builtinCommands, aliasManager, aliasRepo, mruRepo)
        stateMachine = LauncherStateMachine(
            mruRepo = mruRepo,
            commandParser = commandParser,
            aliasRepo = aliasRepo,
            scope = lifecycleScope
        )

        setContent {
            TerminalScreen(
                bridge = bridge,
                appManager = appManager,
                stateMachine = stateMachine,
                themeRepo = themeRepo,
                systemOps = systemOps,
                aliasRepo = aliasRepo,
                reFocusTrigger = reFocusTrigger.value,
                onShowKeyboard = { showKeyboard() }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        stateMachine.onNewIntent()
        reFocusTrigger.value++
        showKeyboard()
    }

    override fun onDestroy() {
        super.onDestroy()
        appManager.unregister()
    }

    private fun showKeyboard() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.show(WindowInsetsCompat.Type.ime())
    }
}

@Composable
fun TerminalScreen(
    bridge: TermuxBridge,
    appManager: AppManager,
    stateMachine: LauncherStateMachine,
    themeRepo: ThemeRepository,
    systemOps: SystemOperations,
    aliasRepo: AliasRepository,
    reFocusTrigger: Int,
    onShowKeyboard: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var logs by remember {
        mutableStateOf(
            listOf(
                "Ash Launcher v0.4.0 [Ready]",
                "Type app name, built-in command, or '$ <cmd>'."
            )
        )
    }

    var currentPreset by remember { mutableStateOf(themeRepo.getTheme()) }
    val palette = ThemePresets.get(currentPreset)
    val historyBuffer = remember { InMemoryHistoryBuffer() }
    val autoCompleteEngine: AutoCompleteEngine = remember { DefaultAutoCompleteEngine() }
    val clipboardManager = LocalClipboardManager.current

    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    fun appendLog(msg: String) {
        logs = logs + msg
    }

    fun submit() {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return

        val cmd = trimmed
        input = ""
        historyBuffer.add(cmd)
        appendLog("❯ $cmd")

        val apps = appManager.installedApps
        when (val action = stateMachine.onInput(cmd, apps)) {
            is CommandAction.Launch -> {
                val note = if (action.source == "mru") " (MRU)" else ""
                appendLog("[Launching ${action.app.label}$note]")
                val ok = appManager.launchApp(action.app)
                if (!ok) {
                    appendLog("Error: failed to launch ${action.app.label}")
                }
            }
            is CommandAction.ShowChoices -> {
                appendLog(action.choicesText)
            }
            is CommandAction.Message -> {
                action.text.lines().forEach { appendLog(it) }
            }
            is CommandAction.ExecuteShell -> {
                appendLog("[Running: $ ${action.shellCommand}...]")
                bridge.execute(action.shellCommand, action.timeoutSec) { result ->
                    if (result.stdout.isNotBlank()) {
                        result.stdout.trimEnd().lines().forEach { appendLog(it) }
                    }
                    if (result.stderr.isNotBlank()) {
                        result.stderr.trimEnd().lines().forEach { appendLog(it) }
                    }
                    val statusLine = if (result.exitCode == 0) {
                        val trunc = if (result.isTruncated) " [output truncated]" else ""
                        "[Exit: 0]$trunc"
                    } else {
                        result.uiMessage
                    }
                    appendLog(statusLine)
                }
            }
            is CommandAction.SetTheme -> {
                currentPreset = action.theme
                appendLog(action.message)
            }
            is CommandAction.ClearLogs -> {
                logs = emptyList()
            }
            CommandAction.None -> {}
        }
    }

    // Auto-scroll to bottom whenever logs change
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    // Auto-focus keyboard on launch & onNewIntent
    LaunchedEffect(reFocusTrigger) {
        delay(100)
        focusRequester.requestFocus()
        onShowKeyboard()
    }

    // Back button behavior
    BackHandler(enabled = true) {
        if (input.isNotEmpty()) {
            input = ""
        } else if (stateMachine.state is LauncherState.AwaitingSelection) {
            stateMachine.onBack()
            appendLog("[Selection cancelled]")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                focusRequester.requestFocus()
                onShowKeyboard()
            }
    ) {
        // Stationary Top Header (Bloody ASCII "ega" banner, system info, diamond separator)
        TopHeader(
            palette = palette,
            systemInfo = systemOps.getBatteryInfo()
        )

        // Middle scrollable terminal log viewer
        TerminalLogViewer(
            logs = logs,
            palette = palette,
            listState = listState,
            modifier = Modifier.weight(1f),
            onLogClick = {
                focusRequester.requestFocus()
                onShowKeyboard()
            }
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Mobile Quick Action Toolbar
        QuickActionToolbar(
            palette = palette,
            onClear = {
                if (input.isNotEmpty()) {
                    input = ""
                } else {
                    logs = emptyList()
                }
                focusRequester.requestFocus()
                onShowKeyboard()
            },
            onPaste = {
                val clip = clipboardManager.getText()?.text
                if (!clip.isNullOrEmpty()) {
                    input = if (input.isEmpty()) clip else "$input$clip"
                }
                focusRequester.requestFocus()
                onShowKeyboard()
            },
            onHistoryPrev = {
                val prev = historyBuffer.getPrevious(input)
                if (prev != null) {
                    input = prev
                }
                focusRequester.requestFocus()
                onShowKeyboard()
            },
            onHistoryNext = {
                val next = historyBuffer.getNext()
                if (next != null) {
                    input = next
                }
                focusRequester.requestFocus()
                onShowKeyboard()
            },
            onTab = {
                val appLabels = appManager.installedApps.map { it.label }
                val aliasKeys = aliasRepo.getAll().keys
                val result = autoCompleteEngine.complete(input, appLabels, aliasKeys)
                if (result.completedText != null) {
                    input = result.completedText
                }
                if (result.message != null) {
                    appendLog(result.message)
                }
                focusRequester.requestFocus()
                onShowKeyboard()
            }
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Bottom 2-line Prompt Footer (Powerline glyphs & prompt input)
        PromptFooter(
            input = input,
            onInputChange = { input = it },
            onSubmit = { submit() },
            palette = palette,
            focusRequester = focusRequester
        )
    }
}
