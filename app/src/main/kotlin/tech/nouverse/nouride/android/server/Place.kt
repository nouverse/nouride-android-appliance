package tech.nouverse.nouride.android.server

import android.content.Context
import tech.nouverse.nouride.android.Layout

/** Where Nouride lives: on this phone, as the app has always done, or on another computer this phone opens. */
enum class Where { PHONE, REMOTE }

/**
 * The person's answer to "where should Nouride run", kept in the app's own private storage.
 *
 * Nothing is started before it is answered: a phone that is only going to open a Nouride somewhere else should not
 * unpack Debian and install one of its own. A phone that already has Nouride on it was answered before the question
 * existed, so it is never asked (`answer`). An install that was running here is left alone on disk when the answer
 * turns to another computer, so choosing this phone again picks it up as it was.
 */
class Place(context: Context) {
  private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
  private val layout = Layout(context)
  private val server = context.getSharedPreferences(ServerService.PREFS, Context.MODE_PRIVATE)

  /** Null until the person has chosen, and only on a phone that has no Nouride of its own yet. */
  fun where(): Where? = answer(prefs.getString(PREF_WHERE, null), installedHere())

  /** The other computer's address as `RemoteHost` made it, or null when Nouride lives on this phone. */
  fun address(): String? = if (where() == Where.REMOTE) lastAddress() else null

  /** The last address given, kept after switching back to this phone so switching again needs no typing. */
  fun lastAddress(): String? = prefs.getString(PREF_ADDRESS, null)

  /** Nouride lives here: what the app did before there was a choice. */
  fun onThisPhone() {
    prefs.edit().putString(PREF_WHERE, Where.PHONE.name).apply()
  }

  /** Nouride lives at [base], a URL `RemoteHost` accepted. */
  fun onMachine(base: String) {
    prefs.edit().putString(PREF_WHERE, Where.REMOTE.name).putString(PREF_ADDRESS, base).apply()
  }

  /** Anything of the phone's own Nouride on disk, or a start that was asked for: the phone was answered already. */
  private fun installedHere(): Boolean =
    layout.rootfs.isDirectory || layout.rootfsPartial.exists() || server.getBoolean(ServerService.PREF_RUN, false)

  companion object {
    private const val PREFS = "place"
    private const val PREF_WHERE = "where"
    private const val PREF_ADDRESS = "address"

    /**
     * The answer from what was stored and whether this phone has Nouride already. Pure, so an update over an install
     * that predates the question is tested to land on that install rather than on the question.
     */
    fun answer(stored: String?, installedHere: Boolean): Where? =
      stored?.let { name -> Where.values().firstOrNull { it.name == name } }
        ?: if (installedHere) Where.PHONE else null
  }
}
