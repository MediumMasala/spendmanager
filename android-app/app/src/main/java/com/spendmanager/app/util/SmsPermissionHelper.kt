package com.spendmanager.app.util

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Helper for SMS permissions with OnePlus/OxygenOS specific optimizations.
 *
 * OnePlus devices (including OnePlus Nord) have aggressive battery management
 * that can kill background receivers. This helper provides:
 *
 * 1. Runtime permission requests for READ_SMS and RECEIVE_SMS
 * 2. Battery optimization exemption request
 * 3. OnePlus-specific auto-start permission guidance
 * 4. Deep linking to OnePlus battery settings
 */
object SmsPermissionHelper {

    const val SMS_PERMISSION_REQUEST_CODE = 1001
    const val BATTERY_OPTIMIZATION_REQUEST_CODE = 1002

    private val SMS_PERMISSIONS = arrayOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS
    )

    /**
     * Check if SMS permissions are granted.
     */
    fun hasSmsPermissions(context: Context): Boolean {
        return SMS_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Request SMS permissions.
     */
    fun requestSmsPermissions(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            SMS_PERMISSIONS,
            SMS_PERMISSION_REQUEST_CODE
        )
    }

    /**
     * Check if battery optimization is disabled for this app.
     */
    fun isBatteryOptimizationDisabled(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Request to disable battery optimization.
     * This is crucial for OnePlus devices to prevent the SMS receiver from being killed.
     */
    fun requestDisableBatteryOptimization(activity: Activity) {
        if (!isBatteryOptimizationDisabled(activity)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            activity.startActivityForResult(intent, BATTERY_OPTIMIZATION_REQUEST_CODE)
        }
    }

    /**
     * Check if device is OnePlus.
     */
    fun isOnePlusDevice(): Boolean {
        return Build.MANUFACTURER.equals("OnePlus", ignoreCase = true)
    }

    /**
     * Check if device is Oppo/Realme (similar OxygenOS/ColorOS battery management).
     */
    fun isOppoRealmeDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return manufacturer == "oppo" || manufacturer == "realme"
    }

    /**
     * Check if device needs special battery/auto-start handling.
     */
    fun needsSpecialHandling(): Boolean {
        return isOnePlusDevice() || isOppoRealmeDevice()
    }

    /**
     * Get intent to open OnePlus auto-start settings.
     * OnePlus requires auto-start permission for background receivers to work reliably.
     */
    fun getOnePlusAutoStartIntent(context: Context): Intent? {
        val intents = listOf(
            // OnePlus OxygenOS 11+ / ColorOS 11+
            Intent().apply {
                component = ComponentName(
                    "com.oneplus.security",
                    "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                )
            },
            // OnePlus OxygenOS 10 and below
            Intent().apply {
                component = ComponentName(
                    "com.oneplus.security",
                    "com.oneplus.security.chainlaunch.view.ChainLaunchAppListAct"
                )
            },
            // Oppo/Realme ColorOS
            Intent().apply {
                component = ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                )
            },
            // Alternative Oppo path
            Intent().apply {
                component = ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.startupapp.StartupAppListActivity"
                )
            },
            // Fallback to app info settings
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        )

        return intents.firstOrNull { intent ->
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
        }
    }

    /**
     * Get intent for OnePlus battery optimization settings.
     */
    fun getOnePlusBatteryOptimizationIntent(context: Context): Intent? {
        val intents = listOf(
            // OnePlus OxygenOS battery optimization
            Intent().apply {
                component = ComponentName(
                    "com.oneplus.security",
                    "com.oneplus.security.battery.BgOptimizeBatteryListActivity"
                )
            },
            // Alternative OnePlus path
            Intent().apply {
                component = ComponentName(
                    "com.oneplus.security",
                    "com.oneplus.security.power.PowerSaveSettingActivity"
                )
            },
            // Oppo/ColorOS battery
            Intent().apply {
                component = ComponentName(
                    "com.coloros.oppoguardelf",
                    "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"
                )
            },
            // Generic Android battery settings
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        )

        return intents.firstOrNull { intent ->
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
        }
    }

    /**
     * Get human-readable instructions for OnePlus devices.
     */
    fun getOnePlusInstructions(): List<String> {
        return listOf(
            "1. Enable SMS permissions when prompted",
            "2. Go to Settings > Apps > Spend Manager > Battery",
            "3. Select 'Don't optimize' or 'No restrictions'",
            "4. Go to Settings > Apps > Manage apps > Spend Manager",
            "5. Tap 'Auto-launch' and enable it",
            "6. Under 'Battery saver', select 'No restrictions'"
        )
    }

    /**
     * Get short instructions for UI display.
     */
    fun getShortInstructions(): String {
        return when {
            isOnePlusDevice() -> "OnePlus detected: After enabling SMS, also enable Auto-start and disable Battery optimization for reliable SMS capture."
            isOppoRealmeDevice() -> "Enable Auto-start and disable Battery optimization in Settings > Apps for reliable SMS capture."
            else -> "Enable SMS permissions to capture bank transaction messages automatically."
        }
    }

    /**
     * Data class representing overall permission status.
     */
    data class PermissionStatus(
        val smsGranted: Boolean,
        val batteryOptimizationDisabled: Boolean,
        val needsSpecialHandling: Boolean,
        val allOptimal: Boolean
    )

    /**
     * Get comprehensive permission status.
     */
    fun getPermissionStatus(context: Context): PermissionStatus {
        val smsGranted = hasSmsPermissions(context)
        val batteryDisabled = isBatteryOptimizationDisabled(context)
        val needsSpecial = needsSpecialHandling()

        return PermissionStatus(
            smsGranted = smsGranted,
            batteryOptimizationDisabled = batteryDisabled,
            needsSpecialHandling = needsSpecial,
            allOptimal = smsGranted && batteryDisabled
        )
    }
}
