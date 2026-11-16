package com.ega.terminallauncher.model

import android.os.UserHandle

data class AppEntry(
    val label: String,
    val packageName: String,
    val activityName: String = "",
    val userHandle: UserHandle? = null
)
