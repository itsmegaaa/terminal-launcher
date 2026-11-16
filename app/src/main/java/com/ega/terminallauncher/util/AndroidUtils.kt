package com.ega.terminallauncher.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager

object BatteryHelper {
    fun getBatteryInfo(context: Context): String {
        return try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val intent = context.registerReceiver(null, filter)
                ?: return "[Error: Battery status unavailable]"

            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level < 0 || scale <= 0) {
                return "[Error: Battery status unavailable]"
            }

            val percent = (level * 100) / scale

            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val statusStr = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                BatteryManager.BATTERY_STATUS_FULL -> "Full"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
                else -> "Unknown"
            }

            val tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            val tempCelsius = tempRaw / 10.0

            "[Battery: $percent% | Status: $statusStr | Temp: ${tempCelsius}°C]"
        } catch (e: Exception) {
            "[Error: Battery status unavailable: ${e.message}]"
        }
    }
}

class FlashlightHelper(context: Context) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private var isTorchOn: Boolean = false
    private var primaryCameraId: String? = null

    init {
        try {
            cameraManager?.let { cm ->
                primaryCameraId = cm.cameraIdList.firstOrNull { id ->
                    val chars = cm.getCameraCharacteristics(id)
                    val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    val facing = chars.get(CameraCharacteristics.LENS_FACING)
                    hasFlash && (facing == CameraCharacteristics.LENS_FACING_BACK || facing == null)
                } ?: cm.cameraIdList.firstOrNull { id ->
                    cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }

                cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                        super.onTorchModeChanged(cameraId, enabled)
                        if (cameraId == primaryCameraId) {
                            isTorchOn = enabled
                        }
                    }
                }, null)
            }
        } catch (_: Exception) {}
    }

    fun toggle(): String {
        val cm = cameraManager
            ?: return "[Error: Flashlight unavailable: Camera service not found]"
        val camId = primaryCameraId
            ?: return "[Error: Flashlight unavailable: device has no flash unit]"

        return try {
            val newState = !isTorchOn
            cm.setTorchMode(camId, newState)
            isTorchOn = newState
            if (newState) "[Flashlight ON]" else "[Flashlight OFF]"
        } catch (e: CameraAccessException) {
            "[Error: Flashlight unavailable: ${e.message}]"
        } catch (e: Exception) {
            "[Error: Flashlight unavailable: ${e.message}]"
        }
    }
}

object VolumeHelper {
    fun handleVolume(context: Context, args: String = ""): String {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return "[Error: Audio service unavailable]"

        val stream = AudioManager.STREAM_MUSIC
        val maxVol = audioManager.getStreamMaxVolume(stream)
        val curVol = audioManager.getStreamVolume(stream)

        val trimmed = args.trim()
        if (trimmed.isEmpty()) {
            val pct = if (maxVol > 0) (curVol * 100) / maxVol else 0
            return "[Volume: $curVol/$maxVol ($pct%)]"
        }

        val isPercent = trimmed.endsWith("%")
        val numStr = trimmed.removeSuffix("%").trim()
        val num = numStr.toIntOrNull()
            ?: return "[Error: Invalid volume level]"

        if (num < 0) {
            return "[Error: Invalid volume level]"
        }

        val targetVol = if (isPercent || num > maxVol) {
            val pct = num.coerceIn(0, 100)
            ((pct * maxVol) + 50) / 100
        } else {
            num.coerceIn(0, maxVol)
        }

        return try {
            audioManager.setStreamVolume(stream, targetVol, AudioManager.FLAG_SHOW_UI)
            val finalPct = if (maxVol > 0) (targetVol * 100) / maxVol else 0
            "[Volume set to $targetVol/$maxVol ($finalPct%)]"
        } catch (e: SecurityException) {
            "[Error: Cannot change volume due to system policy: ${e.message}]"
        } catch (e: Exception) {
            "[Error: Failed to set volume: ${e.message}]"
        }
    }
}
