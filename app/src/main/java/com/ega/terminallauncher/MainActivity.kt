package com.ega.terminallauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ega.terminallauncher.bridge.TermuxBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var bridge: TermuxBridge

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bridge = TermuxBridge(this)

        setContent {
            SpikeScreen(bridge)
        }
    }
}

@Composable
fun SpikeScreen(bridge: TermuxBridge) {
    var logs by remember { mutableStateOf("=== Termux Bridge Spike Ready ===\nTekan tombol di bawah untuk menjalankan pengujian matriks.\n") }
    var customCommand by remember { mutableStateOf("echo 'hello from spike'") }
    var isRunning by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    fun appendLog(msg: String) {
        logs += "\n" + msg
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF22211F))
            .padding(16.dp)
    ) {
        Text(
            text = "Termux IPC Spike Tester",
            color = Color(0xFFD97757),
            fontSize = 20.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Custom Input
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = customCommand,
                onValueChange = { customCommand = it },
                label = { Text("Command", color = Color(0xFF8B8680)) },
                modifier = Modifier.weight(1f),
                textStyle = LocalTextStyle.current.copy(
                    color = Color(0xFFEAE5D9),
                    fontFamily = FontFamily.Monospace
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (!isRunning && customCommand.isNotBlank()) {
                        isRunning = true
                        appendLog("[RUN] $customCommand")
                        bridge.execute(customCommand) { res ->
                            isRunning = false
                            appendLog("Result ID: ${res.requestCode} | Exit: ${res.exitCode} | ${res.durationMs}ms")
                            if (res.stdout.isNotBlank()) appendLog("STDOUT: ${res.stdout.trimEnd()}")
                            if (res.stderr.isNotBlank()) appendLog("STDERR: ${res.stderr.trimEnd()}")
                            appendLog("UI: ${res.uiMessage}\n---")
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97757))
            ) {
                Text("Run")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Quick Matrix Test Buttons
        Text("Matriks Pengujian:", color = Color(0xFFEAE5D9), fontSize = 14.sp)
        Spacer(modifier = Modifier.height(4.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    appendLog("\n[TEST 1] Normal echo...")
                    bridge.execute("echo 'bridge-ok'") { res ->
                        val pass = res.stdout.trimEnd() == "bridge-ok" && res.exitCode == 0
                        appendLog("TEST 1: ${if (pass) "PASS" else "FAIL"} (${res.durationMs}ms) | stdout='${res.stdout.trimEnd()}'")
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D2B28))
            ) { Text("1. Echo", fontSize = 11.sp, color = Color(0xFFEAE5D9)) }

            Button(
                onClick = {
                    appendLog("\n[TEST 2] Quoting & Shell Syntax...")
                    bridge.execute("echo \"a'b \$HOME \\n c\"") { res ->
                        val expectedEnd = "\\n c"
                        val pass = res.stdout.contains("/data/data/com.termux/files/home") && res.stdout.trimEnd().endsWith(expectedEnd)
                        appendLog("TEST 2: ${if (pass) "PASS" else "FAIL"} (${res.durationMs}ms)\nSTDOUT: ${res.stdout.trimEnd()}")
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D2B28))
            ) { Text("2. Quoting", fontSize = 11.sp, color = Color(0xFFEAE5D9)) }

            Button(
                onClick = {
                    appendLog("\n[TEST 4] Stderr non-zero...")
                    bridge.execute("ls /nonexistent_spike_dir") { res ->
                        val pass = res.exitCode != 0 && res.stderr.contains("No such file")
                        appendLog("TEST 4: ${if (pass) "PASS" else "FAIL"} | Exit: ${res.exitCode}\nSTDERR: ${res.stderr.trimEnd()}")
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D2B28))
            ) { Text("4. Stderr", fontSize = 11.sp, color = Color(0xFFEAE5D9)) }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    appendLog("\n[TEST 5] Timeout 5s on sleep 60...")
                    bridge.execute("sleep 60", timeoutSec = 5) { res ->
                        val pass = res.exitCode == 124 && res.durationMs in 4800..6500
                        appendLog("TEST 5: ${if (pass) "PASS" else "FAIL"} | Exit: ${res.exitCode} (${res.durationMs}ms) | ${res.uiMessage}")
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D2B28))
            ) { Text("5. Timeout 5s", fontSize = 11.sp, color = Color(0xFFEAE5D9)) }

            Button(
                onClick = {
                    appendLog("\n[TEST 7] Benchmark Latency 20x 'echo hi'...")
                    scope.launch(Dispatchers.Default) {
                        val latencies = mutableListOf<Long>()
                        for (i in 1..20) {
                            val done = java.util.concurrent.CountDownLatch(1)
                            bridge.execute("echo hi") { res ->
                                latencies.add(res.durationMs)
                                done.countDown()
                            }
                            done.await()
                            delay(50)
                        }
                        latencies.sort()
                        val median = latencies[latencies.size / 2]
                        val max = latencies.last()
                        val min = latencies.first()
                        appendLog("TEST 7 BENCHMARK: Median=${median}ms, Min=${min}ms, Max=${max}ms")
                        appendLog("GO Requirement (<1000ms): ${if (median < 1000) "PASS" else "FAIL"}")
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D2B28))
            ) { Text("7. Benchmark 20x", fontSize = 11.sp, color = Color(0xFFEAE5D9)) }

            Button(
                onClick = {
                    appendLog("\n[TEST 14] Collision 2x rapid...")
                    bridge.execute("echo 'first'") { res1 ->
                        appendLog("Collision 1: code=${res1.requestCode} stdout='${res1.stdout.trimEnd()}'")
                    }
                    bridge.execute("echo 'second'") { res2 ->
                        appendLog("Collision 2: code=${res2.requestCode} stdout='${res2.stdout.trimEnd()}'")
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D2B28))
            ) { Text("14. Collision", fontSize = 11.sp, color = Color(0xFFEAE5D9)) }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    appendLog("\n[TEST 17] Checking \$PATH...")
                    bridge.execute("echo \"\$PATH\"") { res ->
                        appendLog("TEST 17 PATH: ${res.stdout.trimEnd()}")
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D2B28))
            ) { Text("17. Check \$PATH", fontSize = 11.sp, color = Color(0xFFEAE5D9)) }

            Button(
                onClick = { logs = "=== Logs Cleared ===\n" },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B2D2B))
            ) { Text("Clear Logs", fontSize = 11.sp, color = Color(0xFFC45C50)) }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Logs viewer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1918))
                .padding(8.dp)
                .verticalScroll(scrollState)
        ) {
            Text(
                text = logs,
                color = Color(0xFFEAE5D9),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
