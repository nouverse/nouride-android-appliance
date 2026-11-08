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
   * Xiaomi's phones (Xiaomi, Redmi, POCO; MIUI and HyperOS) keep an app from starting by itself until its Autostart is
   * on: without it Nouride does not come back after the phone restarts (no BOOT_COMPLETED arrives). Nothing tells an
   * app whether it is on, so the More tab names the switch on these phones only.
   */
  fun asksForAutostart(manufacturer: String = Build.MANUFACTURER, brand: String = Build.BRAND): Boolean =
    listOf(manufacturer, brand).any { it.lowercase() in setOf("xiaomi", "redmi", "poco") }

  /** The app's page in Settings, where vendor battery options (HyperOS: No restrictions) live. */
  fun appDetails(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
}
