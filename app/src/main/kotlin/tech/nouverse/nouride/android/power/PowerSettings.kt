package tech.nouverse.nouride.android.power

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/** Android's own battery-optimization exemption; vendor battery savers have their own switches. */
object PowerSettings {
  fun isExempt(context: Context): Boolean =
    Build.VERSION.SDK_INT < 23 ||
      (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)

  /** The system dialog that asks the person to let the app run unrestricted. */
  @SuppressLint("BatteryLife")
  fun exemptionRequest(context: Context): Intent =
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

  /**
   * Xiaomi's phones (Xiaomi, Redmi, POCO; MIUI and HyperOS) can keep an app from starting by itself until its Autostart
   * is on. Which broadcasts that holds back differs between versions: on a Redmi Note 12 with HyperOS 2.0.202 (global),
   * 2026-11-08, BOOT_COMPLETED reached both Nouride Home and its Dev build with Autostart off, while an app update's
   * MY_PACKAGE_REPLACED had been refused on the same phone ("process is not permitted to auto start"). Nothing tells an
   * app whether it is on, so the More tab names the switch on these phones only, as something that may be needed.
   */
  fun asksForAutostart(manufacturer: String = Build.MANUFACTURER, brand: String = Build.BRAND): Boolean =
    listOf(manufacturer, brand).any { it.lowercase() in setOf("xiaomi", "redmi", "poco") }

  /** The app's page in Settings, where vendor battery options (HyperOS: No restrictions) live. */
  fun appDetails(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

  /** Xiaomi's list of the apps allowed to start by themselves, each with its own switch (Security's Autostart). */
  const val AUTOSTART_LIST = "miui.intent.action.OP_AUTO_START"

  /**
   * Where the person turns Autostart on: Xiaomi's Autostart list when the phone has one an app may open, otherwise the
   * app's page in Settings. The app's page was all this opened before, and on HyperOS 2 (a Redmi Note 12, 2026-11-08)
   * that page has no Autostart switch at all: the person was sent to look for a switch that was not there.
   */
  fun autostartSettings(context: Context): Intent {
    val list = Intent(AUTOSTART_LIST)
    val opens = context.packageManager.resolveActivity(list, 0)?.activityInfo?.exported == true
    return if (autostartScreen(opens) == AUTOSTART_LIST) list else appDetails(context)
  }

  /** The screen [autostartSettings] opens, by whether the phone's Autostart list [opens] for an app. Pure, so tested. */
  fun autostartScreen(opens: Boolean): String = if (opens) AUTOSTART_LIST else Settings.ACTION_APPLICATION_DETAILS_SETTINGS
}
