package tech.nouverse.nouride.android.update

import android.content.Context
import tech.nouverse.nouride.android.server.Place
import tech.nouverse.nouride.android.server.ServerState
import tech.nouverse.nouride.android.server.Where

/**
 * A newer Nouride this phone's Nouride can put in, as last heard from its update route (`NourideUpdates`): what Home's
 * card, More's dot and line, and the notification say, as they say the app's own update (`Updates`). Without it a new
 * Nouride was only seen by opening More, then Versions and updates (the owner, 2026-11-16).
 *
 * Asked when the app opens and twice a day from the service, with Nouride's own few minutes of cache, never past it:
 * the release channel is Nouride's to ask. Kept until an answer says otherwise, as no answer is not "up to date"; and
 * dropped by itself once the Nouride running is that version or newer. Nouride on another computer has its own
 * dashboard to say so: nothing here.
 */
object NourideNotice {
  private const val PREFS = "nouride_update"
  private const val READY = "ready"
  private const val CHECKED_AT = "checked_at"

  /** The version waiting, or null. Cheap: what was kept, held against the version running now. */
  fun waiting(context: Context): String? {
    if (Place(context).where() == Where.REMOTE) return null
    val ready = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(READY, null)
    return stillWaiting(ready, ServerState.health?.version?.removePrefix("v"))
  }

  /** Keeps what [status] says, unless it could not ask: then the last answer stands. */
  fun remember(context: Context, status: NourideStatus) {
    if (status.checkFailed) return
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
      .putString(READY, noticeFor(status))
      .putLong(CHECKED_AT, System.currentTimeMillis())
      .apply()
  }

  /**
   * Asks Nouride when the last answer is older than [maxAgeMs] and it answers; blocking, off the main thread. A failed
   * call keeps the last answer.
   */
  fun check(context: Context, maxAgeMs: Long) {
    if (Place(context).where() == Where.REMOTE) return
    if (ServerState.phase != ServerState.Phase.RUNNING || ServerState.health?.ok != true) return
    val at = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(CHECKED_AT, 0)
    if (System.currentTimeMillis() - at < maxAgeMs) return
    try {
      remember(context, NourideUpdates.status(context, force = false))
    } catch (e: Exception) {
      // Nouride busy or restarting: asked again next time.
    }
  }

  /** The version worth a notice in [status]: a newer one this install can put in by itself. Pure. */
  fun noticeFor(status: NourideStatus): String? = status.ready?.takeIf { status.canUpdate }

  /** [ready] while the Nouride running ([running], null when not known) is older than it. Pure. */
  fun stillWaiting(ready: String?, running: String?): String? =
    ready?.takeIf { running == null || UpdateFeed.newer(it, running) }
}
