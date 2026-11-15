package com.devxrohan.smusic.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Several manufacturers ship their own background-task killer on top of
 * stock Android (MIUI, ColorOS/Realme UI, FuntouchOS/OriginOS, EMUI/MagicOS,
 * Samsung's "sleeping apps", etc). These can stop a foreground media-playback
 * service — mid-song, with no warning — even after the user has granted the
 * standard "ignore battery optimizations" permission below, because it is a
 * separate, manufacturer-specific permission ("Autostart", "Protected apps")
 * that stock Android has no API for. SMusic previously only ever asked for
 * the standard permission, and only from deep inside Voice Assistant
 * settings where most people never looked — which is the main reason
 * playback randomly stopped on some phones and not others.
 */
object OemBackgroundHelper {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return true
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Opens the system dialog asking to exempt this app from battery optimization. */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }.onFailure {
            // Some OEM builds strip this action out entirely; fall back to the
            // generic list of apps so the user can still find SMusic in it.
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    /** True for manufacturers known to restrict background apps beyond stock Android. */
    fun isKnownAggressiveOem(): Boolean = normalizedManufacturer() in AGGRESSIVE_OEMS

    /** Short, user-facing name for what this manufacturer calls its background-permission screen. */
    fun autostartScreenLabel(): String =
        when (normalizedManufacturer()) {
            "xiaomi", "redmi", "poco" -> "Autostart"
            "oppo", "realme" -> "Startup Manager"
            "vivo", "iqoo" -> "Autostart"
            "huawei", "honor" -> "App launch"
            "samsung" -> "Battery usage"
            "asus" -> "Auto-start Manager"
            "letv" -> "Background permission"
            "meizu" -> "Permission manager"
            else -> "Background permissions"
        }

    /**
     * Opens the current manufacturer's own autostart/background-permission
     * screen. These screens aren't part of the public Android SDK and their
     * exact component name can change between firmware builds, so every
     * candidate is checked with [android.content.pm.PackageManager.resolveActivity]
     * before it's launched, and if none resolve this falls back to the app's
     * own system "App info" page, which still carries the same toggle on most
     * of these skins, just under a different name.
     */
    fun openAutostartSettings(context: Context) {
        for (intent in candidateIntents()) {
            val opened = runCatching {
                if (context.packageManager.resolveActivity(intent, 0) != null) {
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    true
                } else {
                    false
                }
            }.getOrDefault(false)
            if (opened) return
        }
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }

    private fun normalizedManufacturer(): String = Build.MANUFACTURER.lowercase()

    private val AGGRESSIVE_OEMS = setOf(
        "xiaomi", "redmi", "poco", "oppo", "realme", "vivo", "iqoo",
        "huawei", "honor", "asus", "letv", "meizu", "nubia", "zte", "samsung",
    )

    private fun component(pkg: String, cls: String) = Intent().apply {
        component = ComponentName(pkg, cls)
    }

    private fun candidateIntents(): List<Intent> {
        val manufacturerSpecific = when (normalizedManufacturer()) {
            "xiaomi", "redmi", "poco" -> listOf(
                component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                component("com.miui.securitycenter", "com.miui.powercenter.PowerSettings"),
            )
            "oppo", "realme" -> listOf(
                component("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                component("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                component("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
                component("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"),
            )
            "vivo", "iqoo" -> listOf(
                component("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
            )
            "huawei", "honor" -> listOf(
                component(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                ),
                component("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            )
            "asus" -> listOf(
                component("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"),
            )
            "letv" -> listOf(
                component("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity"),
            )
            "meizu" -> listOf(
                component("com.meizu.safe", "com.meizu.safe.permission.PermissionMainActivity"),
            )
            "samsung" -> listOf(
                Intent("com.samsung.android.sm.ACTION_BATTERY").apply {
                    setPackage("com.samsung.android.lool")
                },
            )
            else -> emptyList()
        }
        // A generic Android settings screen that lists every app's battery-optimization
        // state — supported on every device, so it's a safe last resort before the
        // final per-app "App info" fallback in openAutostartSettings().
        val generic = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        return manufacturerSpecific + generic
    }
}
