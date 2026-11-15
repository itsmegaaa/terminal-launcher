package com.ega.terminallauncher.bridge

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object TermuxBridgeConstants {
    const val TERMUX_PACKAGE = "com.termux"
    const val TERMUX_SERVICE = "com.termux.app.RunCommandService"
    const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"

    const val EXTRA_RUN_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    const val EXTRA_RUN_COMMAND_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    const val EXTRA_RUN_COMMAND_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    const val EXTRA_RUN_COMMAND_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"

    const val RESULT_BUNDLE_KEY = "com.termux.execute.result"
    const val ACTION_TERMUX_RESULT = "com.ega.terminallauncher.ACTION_TERMUX_RESULT"

    const val DEFAULT_TIMEOUT_PATH = "/data/data/com.termux/files/usr/bin/timeout"
    const val DEFAULT_BASH_PATH = "/data/data/com.termux/files/usr/bin/bash"
    const val DEFAULT_WORKDIR = "/data/data/com.termux/files/home"
}

data class TermuxExecutionResult(
    val requestCode: Int,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val errCode: Int,
    val errmsg: String,
    val durationMs: Long,
    val uiMessage: String
)

data class ExecutionContext(
    val requestCode: Int,
    val startTimeMs: Long,
    val timeoutSec: Int,
    val pendingIntent: PendingIntent,
    val callback: (TermuxExecutionResult) -> Unit,
    val watchdogRunnable: Runnable
)

object ExitCodeMapper {
    fun mapExitCode(exitCode: Int, durationMs: Long, timeoutSec: Int): String {
        return when (exitCode) {
            0 -> "Success"
            124 -> "[Command timed out after ${timeoutSec}s]"
            137 -> {
                val timeoutMarginMs = (timeoutSec * 1000L) - 500L
                if (durationMs >= timeoutMarginMs) {
                    "[Command force-killed after timeout]"
                } else {
                    "[Command killed by system / OOM]"
                }
            }
            else -> "[Process exited with code $exitCode]"
        }
    }
}

object BridgeCommandBuilder {
    fun buildTimeoutArguments(cmd: String, timeoutSec: Int = 30): Array<String> {
        return arrayOf(
            "-k", "2",
            timeoutSec.toString(),
            TermuxBridgeConstants.DEFAULT_BASH_PATH,
            "-c",
            cmd
        )
    }
}

object ExecutionIdManager {
    private val counter = AtomicInteger(1000)
    private val activeContexts = ConcurrentHashMap<Int, ExecutionContext>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun nextRequestCode(): Int = counter.incrementAndGet()

    fun register(
        requestCode: Int,
        timeoutSec: Int,
        pendingIntent: PendingIntent,
        callback: (TermuxExecutionResult) -> Unit
    ) {
        val watchdogDelayMs = (timeoutSec + 5) * 1000L
        val watchdogRunnable = Runnable {
            val context = activeContexts.remove(requestCode)
            if (context != null) {
                try {
                    context.pendingIntent.cancel()
                } catch (_: Exception) {}
                
                val durationMs = System.currentTimeMillis() - context.startTimeMs
                val timeoutResult = TermuxExecutionResult(
                    requestCode = requestCode,
                    stdout = "",
                    stderr = "[UI Watchdog Safety Net: proses tidak merespons setelah ${timeoutSec + 5}s]",
                    exitCode = 124,
                    errCode = -1,
                    errmsg = "Watchdog expired",
                    durationMs = durationMs,
                    uiMessage = "[Command timed out after ${timeoutSec}s]"
                )
                context.callback(timeoutResult)
            }
        }

        val ctx = ExecutionContext(
            requestCode = requestCode,
            startTimeMs = System.currentTimeMillis(),
            timeoutSec = timeoutSec,
            pendingIntent = pendingIntent,
            callback = callback,
            watchdogRunnable = watchdogRunnable
        )
        activeContexts[requestCode] = ctx
        mainHandler.postDelayed(watchdogRunnable, watchdogDelayMs)
    }

    fun dispatchResult(requestCode: Int, rawBundle: Bundle?): Boolean {
        val context = activeContexts.remove(requestCode) ?: return false
        mainHandler.removeCallbacks(context.watchdogRunnable)

        val durationMs = System.currentTimeMillis() - context.startTimeMs
        val resultBundle = rawBundle?.getBundle(TermuxBridgeConstants.RESULT_BUNDLE_KEY) ?: rawBundle

        val stdout = resultBundle?.getString("stdout") ?: ""
        val stderr = resultBundle?.getString("stderr") ?: ""
        val exitCode = resultBundle?.getInt("exitCode", -1) ?: -1
        val errCode = resultBundle?.getInt("errCode", 0) ?: 0
        val errmsg = resultBundle?.getString("errmsg") ?: ""

        val uiMessage = ExitCodeMapper.mapExitCode(exitCode, durationMs, context.timeoutSec)

        val result = TermuxExecutionResult(
            requestCode = requestCode,
            stdout = stdout,
            stderr = stderr,
            exitCode = exitCode,
            errCode = errCode,
            errmsg = errmsg,
            durationMs = durationMs,
            uiMessage = uiMessage
        )

        context.callback(result)
        return true
    }

    fun activeCount(): Int = activeContexts.size
}

class TermuxBridge(private val context: Context) {

    fun execute(
        cmd: String,
        timeoutSec: Int = 30,
        workdir: String = TermuxBridgeConstants.DEFAULT_WORKDIR,
        callback: (TermuxExecutionResult) -> Unit
    ): Int {
        val requestCode = ExecutionIdManager.nextRequestCode()

        val callbackIntent = Intent(context, TermuxResultReceiver::class.java).apply {
            action = TermuxBridgeConstants.ACTION_TERMUX_RESULT
            putExtra("requestCode", requestCode)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            callbackIntent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        ExecutionIdManager.register(requestCode, timeoutSec, pendingIntent, callback)

        val intent = Intent().apply {
            setClassName(TermuxBridgeConstants.TERMUX_PACKAGE, TermuxBridgeConstants.TERMUX_SERVICE)
            action = TermuxBridgeConstants.ACTION_RUN_COMMAND
            putExtra(TermuxBridgeConstants.EXTRA_RUN_COMMAND_PATH, TermuxBridgeConstants.DEFAULT_TIMEOUT_PATH)
            putExtra(TermuxBridgeConstants.EXTRA_RUN_COMMAND_ARGUMENTS, BridgeCommandBuilder.buildTimeoutArguments(cmd, timeoutSec))
            putExtra(TermuxBridgeConstants.EXTRA_RUN_COMMAND_WORKDIR, workdir)
            putExtra(TermuxBridgeConstants.EXTRA_RUN_COMMAND_BACKGROUND, true)
            putExtra(TermuxBridgeConstants.EXTRA_PENDING_INTENT, pendingIntent)
        }

        context.startService(intent)
        return requestCode
    }
}
