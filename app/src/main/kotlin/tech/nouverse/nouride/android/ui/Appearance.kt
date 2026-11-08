package tech.nouverse.nouride.android.ui

import android.content.Context
import android.content.res.Configuration

/** Light or dark: as the phone is set, or chosen for this app on the More tab. */
enum class Appearance { PHONE, LIGHT, DARK }

/**
 * The appearance chosen on the More tab, applied to every screen of the app, the dashboard's included: each activity
 * starts from a context whose night mode is the one chosen (`wrap`, in `attachBaseContext`), so the colours
 * (`values-night`), the theme the WebView reads and the system bars all follow it. Android's own per-app setting
 * only exists from Android 12; this works on every version the app runs on.
 */
object Appearances {
  private const val PREFS = "appearance"
  private const val KEY = "mode"

  fun get(context: Context): Appearance =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
      ?.let { saved -> Appearance.values().firstOrNull { it.name == saved } } ?: Appearance.PHONE

  fun set(context: Context, appearance: Appearance) =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, appearance.name).apply()

  /** [uiMode] with its night bits as [appearance] wants them; the phone's own for [Appearance.PHONE]. Pure. */
  fun uiMode(uiMode: Int, appearance: Appearance): Int {
    val night = when (appearance) {
      Appearance.PHONE -> return uiMode
      Appearance.LIGHT -> Configuration.UI_MODE_NIGHT_NO
      Appearance.DARK -> Configuration.UI_MODE_NIGHT_YES
    }
    return (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
  }

  /** The context an activity starts from, in the appearance chosen. */
  fun wrap(base: Context): Context {
    val appearance = get(base)
    if (appearance == Appearance.PHONE) return base
    val config = Configuration(base.resources.configuration)
    config.uiMode = uiMode(config.uiMode, appearance)
    return base.createConfigurationContext(config)
  }
}
