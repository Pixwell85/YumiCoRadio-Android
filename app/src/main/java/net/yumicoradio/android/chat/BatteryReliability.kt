// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Everything the app needs to nudge the user into keeping the background chat connection alive.
 *
 * Android Doze and — far more aggressively — OEM battery managers (MIUI, One UI, ColorOS…) freeze
 * or kill a foreground service that is not exempted. There is no API to override that; the only
 * reliable fix is the user granting the exemption and enabling autostart. This helper reads the
 * exemption state and builds the intents that take the user straight to the right screen, with a
 * universal fallback for the OEM deep-links, which are undocumented and vary by version.
 *
 * The manufacturer mapping and guidance copy are pure (unit-tested). The intent builders are thin
 * Android glue verified on-device.
 */

enum class Oem { XIAOMI, SAMSUNG, OPPO, VIVO, ONEPLUS, REALME, HUAWEI, OTHER }

/** OEM from a raw `Build.MANUFACTURER`-style string. Pure, so the mapping is unit-testable. */
fun oemFromManufacturer(raw: String): Oem = when (raw.trim().lowercase()) {
    "xiaomi", "redmi", "poco" -> Oem.XIAOMI
    "samsung" -> Oem.SAMSUNG
    "oppo" -> Oem.OPPO
    "vivo" -> Oem.VIVO
    "oneplus" -> Oem.ONEPLUS
    "realme" -> Oem.REALME
    "huawei", "honor" -> Oem.HUAWEI
    else -> Oem.OTHER
}

/** Per-OEM guidance copy. `null` for [Oem.OTHER] — the plain battery button is enough there. */
data class OemGuidance(val label: String, val instruction: String)

fun oemGuidance(oem: Oem): OemGuidance? = when (oem) {
    Oem.XIAOMI -> OemGuidance(
        "Xiaomi / HyperOS settings",
        "Enable Background autostart for Yumi Co. Radio, then set its battery mode to " +
            "\"No restrictions\". HyperOS does not let the app verify these two switches.",
    )
    Oem.SAMSUNG -> OemGuidance(
        "Samsung battery settings",
        "On Samsung phones, remove Yumi Co. Radio from \"Sleeping apps\" and allow background " +
            "activity, or the connection will be dropped.",
    )
    Oem.OPPO, Oem.REALME -> OemGuidance(
        "Startup manager",
        "On ColorOS phones, allow Startup and background running for Yumi Co. Radio, or the chat " +
            "will be closed in the background.",
    )
    Oem.VIVO -> OemGuidance(
        "Background startup",
        "On vivo phones, allow background startup and high background power use for Yumi Co. Radio.",
    )
    Oem.ONEPLUS -> OemGuidance(
        "Battery optimization",
        "On OnePlus phones, set battery optimization for Yumi Co. Radio to \"Don't optimize\".",
    )
    Oem.HUAWEI -> OemGuidance(
        "App launch",
        "On Huawei/Honor phones, set App launch for Yumi Co. Radio to Manage manually and enable " +
            "all three toggles.",
    )
    Oem.OTHER -> null
}

enum class BatteryExemption { ALLOWED, RESTRICTED, UNKNOWN }

data class BatteryAction(val label: String)

fun batteryAction(exemption: BatteryExemption): BatteryAction? = when (exemption) {
    BatteryExemption.ALLOWED -> null
    BatteryExemption.RESTRICTED -> BatteryAction("Allow background activity")
    BatteryExemption.UNKNOWN -> BatteryAction("Open battery settings")
}

enum class BatterySettingsDestination { REQUEST_APP_EXEMPTION, OPTIMIZATION_LIST }

fun batterySettingsDestination(exemption: BatteryExemption): BatterySettingsDestination =
    if (exemption == BatteryExemption.RESTRICTED) {
        BatterySettingsDestination.REQUEST_APP_EXEMPTION
    } else {
        BatterySettingsDestination.OPTIMIZATION_LIST
    }

fun batteryExemption(raw: Boolean?): BatteryExemption = when (raw) {
    true -> BatteryExemption.ALLOWED
    false -> BatteryExemption.RESTRICTED
    null -> BatteryExemption.UNKNOWN
}

fun batteryExemptionSummary(exemption: BatteryExemption): String = when (exemption) {
    BatteryExemption.ALLOWED -> "Android battery optimization is disabled for Yumi Co. Radio."
    BatteryExemption.RESTRICTED -> "Android battery saving may suspend the chat."
    BatteryExemption.UNKNOWN -> "Android battery exemption could not be verified."
}

/** Whether Android's standard Doze exemption can be verified. */
fun readBatteryExemption(context: Context): BatteryExemption = batteryExemption(
    runCatching {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.isIgnoringBatteryOptimizations(context.packageName)
    }.getOrNull(),
)

/** Compatibility bridge for the existing UI until it adopts the full three-state result. */
fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    readBatteryExemption(context) == BatteryExemption.ALLOWED

/** Requests this app's exemption directly when Android reports it restricted. */
@SuppressLint("BatteryLife") // Socket.IO is the chat's core transport; no FCM equivalent exists.
fun batterySettingsIntent(context: Context, exemption: BatteryExemption): Intent =
    when (batterySettingsDestination(exemption)) {
        BatterySettingsDestination.REQUEST_APP_EXEMPTION ->
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.fromParts("package", context.packageName, null))
        BatterySettingsDestination.OPTIMIZATION_LIST ->
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }

/** The universal fallback: this app's details page, where every OEM exposes its own controls. */
fun appDetailsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", context.packageName, null))

/**
 * Ordered candidate deep-links to the OEM autostart/battery screen. Tried first-that-resolves;
 * component names are undocumented and version-specific, so [openOemSettings] always falls back.
 */
private fun oemIntents(oem: Oem): List<Intent> {
    fun comp(pkg: String, cls: String) = Intent().setComponent(ComponentName(pkg, cls))
    return when (oem) {
        Oem.XIAOMI -> listOf(
            comp("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        )
        Oem.SAMSUNG -> listOf(
            comp("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
            comp("com.samsung.android.lool", "com.samsung.android.sm.ui.dashboard.SmDashboardActivity"),
        )
        Oem.OPPO, Oem.REALME -> listOf(
            comp("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            comp("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
        )
        Oem.VIVO -> listOf(
            comp("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        )
        Oem.ONEPLUS -> listOf(
            comp("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
        )
        Oem.HUAWEI -> listOf(
            comp("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            comp("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        )
        Oem.OTHER -> emptyList()
    }
}

/** Adds NEW_TASK only when not launched from an Activity (so Back still returns to the app when it is). */
private fun launch(context: Context, intent: Intent): Boolean {
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { context.startActivity(intent) }.isSuccess
}

/** Launches the best available OEM screen, falling back to this app's details page. Never throws. */
fun openOemSettings(context: Context, oem: Oem) {
    for (intent in oemIntents(oem)) {
        if (launch(context, intent)) return
    }
    launch(context, appDetailsIntent(context))
}

/** Launches the system battery-optimization screen, falling back to app details. Never throws. */
fun openBatterySettings(context: Context) {
    if (launch(context, batterySettingsIntent(context, readBatteryExemption(context)))) return
    launch(context, appDetailsIntent(context))
}

/** The current device's OEM, from [Build.MANUFACTURER]. */
fun currentOem(): Oem = oemFromManufacturer(Build.MANUFACTURER ?: "")
