package tech.nouverse.nouride.android.server

import android.content.Context

/** Where Nouride lives: on this phone, as the app has always done, or on another machine this phone opens. */
enum class Where { PHONE, REMOTE }

/**
 * The person's answer to "where should Nouride live", kept in the app's own private storage.
 *
 * Nothing is started before it is answered: a phone that is only going to open a Nouride somewhere else should not
 * unpack Debian and install one of its own. An install that was already running here is left alone on disk, so
 * choosing this phone again picks it up as it was.
 */
class Place(context: Context) {
  private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  /** Null until the person has chosen; never for long, as the app asks on its first screen. */
  fun where(): Where? = prefs.getString(PREF_WHERE, null)
    ?.let { name -> Where.values().firstOrNull { it.name == name } }

  /** The other machine's address as `RemoteHost.base` made it, or null when Nouride lives on this phone. */
  fun address(): String? = if (where() == Where.REMOTE) prefs.getString(PREF_ADDRESS, null) else null

  /** Nouride lives here: what the app did before there was a choice. */
  fun onThisPhone() {
    prefs.edit().putString(PREF_WHERE, Where.PHONE.name).remove(PREF_ADDRESS).apply()
  }

  /** Nouride lives at [base], a URL `RemoteHost.base` accepted. */
  fun onMachine(base: String) {
    prefs.edit().putString(PREF_WHERE, Where.REMOTE.name).putString(PREF_ADDRESS, base).apply()
  }

  /** Asks again: the app shows the question on Home, and nothing starts until it is answered. */
  fun forget() {
    prefs.edit().remove(PREF_WHERE).remove(PREF_ADDRESS).apply()
  }

  private companion object {
    const val PREFS = "place"
    const val PREF_WHERE = "where"
    const val PREF_ADDRESS = "address"
  }
}
