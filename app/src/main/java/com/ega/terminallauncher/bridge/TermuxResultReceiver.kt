package com.ega.terminallauncher.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TermuxResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null) return
        val requestCode = intent.getIntExtra("requestCode", -1)
        if (requestCode != -1) {
            ExecutionIdManager.dispatchResult(requestCode, intent.extras)
        }
    }
}
