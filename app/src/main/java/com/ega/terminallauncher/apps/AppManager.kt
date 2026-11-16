package com.ega.terminallauncher.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import com.ega.terminallauncher.model.AppEntry
import com.ega.terminallauncher.mru.MruRepository

class AppManager(
    private val context: Context,
    private val mruRepo: MruRepository,
    private val onAppsUpdated: ((List<AppEntry>) -> Unit)? = null
) {
    private val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
    private val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
    private val _apps = mutableListOf<AppEntry>()

    val installedApps: List<AppEntry>
        get() = synchronized(_apps) { _apps.toList() }

    private val launcherCallback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            mruRepo.removePackage(packageName)
            loadApps()
        }

        override fun onPackageAdded(packageName: String, user: UserHandle) {
            loadApps()
        }

        override fun onPackageChanged(packageName: String, user: UserHandle) {
            loadApps()
        }

        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
            loadApps()
        }

        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
            loadApps()
        }
    }

    init {
        loadApps()
        register()
    }

    fun register() {
        try {
            launcherApps?.registerCallback(launcherCallback, Handler(Looper.getMainLooper()))
        } catch (_: Exception) {
        }
    }

    fun unregister() {
        try {
            launcherApps?.unregisterCallback(launcherCallback)
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun loadApps() {
        val list = mutableListOf<AppEntry>()
        try {
            if (launcherApps != null && userManager != null) {
                val profiles = userManager.userProfiles ?: listOf(Process.myUserHandle())
                for (profile in profiles) {
                    val activities = launcherApps.getActivityList(null, profile)
                    for (info in activities) {
                        list.add(
                            AppEntry(
                                label = info.label?.toString() ?: info.applicationInfo.packageName,
                                packageName = info.applicationInfo.packageName,
                                activityName = info.name,
                                userHandle = profile
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }

        if (list.isEmpty()) {
            try {
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val resolveInfos: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
                for (ri in resolveInfos) {
                    val label = ri.loadLabel(pm).toString()
                    val pkg = ri.activityInfo.packageName
                    val name = ri.activityInfo.name
                    list.add(AppEntry(label = label, packageName = pkg, activityName = name))
                }
            } catch (_: Exception) {
            }
        }

        _apps.clear()
        _apps.addAll(list.sortedBy { it.label.lowercase() })
        onAppsUpdated?.invoke(installedApps)
    }

    fun launchApp(app: AppEntry): Boolean {
        return try {
            if (launcherApps != null && app.userHandle != null && app.activityName.isNotBlank()) {
                val component = ComponentName(app.packageName, app.activityName)
                launcherApps.startMainActivity(component, app.userHandle, null, null)
                true
            } else {
                val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    true
                } else {
                    false
                }
            }
        } catch (_: Exception) {
            false
        }
    }
}
