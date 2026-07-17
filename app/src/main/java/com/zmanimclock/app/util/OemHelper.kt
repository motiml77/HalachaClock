package com.zmanimclock.app.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Aggressive-OEM battery managers kill background apps even with a battery
 * exemption. This helper detects such devices and deep-links to the vendor's
 * autostart / battery screen so the user can whitelist the app.
 */
object OemHelper {

    private val AGGRESSIVE = setOf(
        "xiaomi", "redmi", "poco", "oppo", "realme", "vivo", "iqoo",
        "huawei", "honor", "oneplus", "samsung", "meizu", "asus", "letv",
    )

    fun isAggressiveOem(): Boolean =
        Build.MANUFACTURER.lowercase() in AGGRESSIVE || Build.BRAND.lowercase() in AGGRESSIVE

    fun oemName(): String = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }

    /** Known vendor autostart/battery screens, best first. */
    private val VENDOR_SCREENS = listOf(
        // Xiaomi / Redmi / Poco
        ComponentName(
            "com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartManagementActivity",
        ),
        // Huawei / Honor
        ComponentName(
            "com.huawei.systemmanager",
            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        ),
        // Oppo / Realme
        ComponentName(
            "com.coloros.safecenter",
            "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        ),
        // Vivo
        ComponentName(
            "com.vivo.permissionmanager",
            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        ),
        // Samsung (battery / sleeping apps)
        ComponentName(
            "com.samsung.android.lool",
            "com.samsung.android.sm.battery.ui.BatteryActivity",
        ),
        // OnePlus
        ComponentName(
            "com.oneplus.security",
            "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
        ),
    )

    /**
     * Opens the vendor's autostart screen; falls back to the app-info page.
     * Always succeeds in opening *something*.
     */
    fun openAutostartSettings(context: Context) {
        for (component in VENDOR_SCREENS) {
            val ok = runCatching {
                context.startActivity(
                    Intent().apply {
                        setComponent(component)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }.isSuccess
            if (ok) return
        }
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
