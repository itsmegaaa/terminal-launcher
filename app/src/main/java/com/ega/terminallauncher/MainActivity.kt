package com.ega.terminallauncher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.ega.terminallauncher.alias.AliasManager
import com.ega.terminallauncher.alias.SharedPrefsAliasRepository
import com.ega.terminallauncher.apps.AppManager
import com.ega.terminallauncher.bridge.TermuxBridge
import com.ega.terminallauncher.command.AndroidSystemOperations
import com.ega.terminallauncher.command.BuiltinCommands
import com.ega.terminallauncher.command.CommandParser
import com.ega.terminallauncher.mru.SharedPrefsMruRepository
import com.ega.terminallauncher.state.CommandAction
import com.ega.terminallauncher.state.LauncherState
import com.ega.terminallauncher.state.LauncherStateMachine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var bridge: TermuxBridge
    private lateinit var mruRepo: SharedPrefsMruRepository
    private lateinit var appManager: AppManager
    private lateinit var stateMachine: LauncherStateMachine

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
        val aliasRepo = SharedPrefsAliasRepository(this)
        val aliasManager = AliasManager(aliasRepo)
        val systemOps = AndroidSystemOperations(this)
        val builtinCommands = BuiltinCommands(systemOps, aliasManager, mruRepo)
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
    reFocusTrigger: Int,
    onShowKeyboard: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var logs by remember {
        mutableStateOf(
            listOf(
                "Ash Launcher v0.3.0 [Ready]",
                "Type app name, built-in command, or '$ <cmd>'."
            )
        )
    }

    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    fun appendLog(msg: String) {
        logs = logs + msg
    }

    fun submit() {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return

        val cmd = trimmed
        input = ""
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
            .background(Color(0xFF1E1E1E))
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
        // Output log area
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(logs) { line ->
                Text(
                    text = line,
                    color = when {
                        line.startsWith("❯") -> Color(0xFF81C784)
                        line.startsWith("[Launching") -> Color(0xFF64B5F6)
                        line.startsWith("[") -> Color(0xFFFFB74D)
                        line.startsWith("Error:") -> Color(0xFFE57373)
                        else -> Color(0xFFE0E0E0)
                    },
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 20.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Input Prompt Line
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "❯ ",
                color = Color(0xFF81C784),
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp
            )

            BasicTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                textStyle = TextStyle(
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp
                ),
                cursorBrush = SolidColor(Color(0xFF81C784)),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(
                    onGo = { submit() }
                )
            )
        }
    }
}
