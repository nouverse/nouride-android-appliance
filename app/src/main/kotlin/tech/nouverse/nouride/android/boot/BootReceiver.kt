package tech.nouverse.nouride.android.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import tech.nouverse.nouride.android.server.ServerService

/**
 * Starts the server again after the phone boots, and after this app is updated (Android stops an app to replace it),
 * if it was running then: an update of Nouride Home must not leave the person's agents off until they open the app.
 * HyperOS delivers neither to an app it does not let autostart; an update from the app itself is started again by
 * `UpdateReceiver` either way.
 */
class BootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val run = context.getSharedPreferences(ServerService.PREFS, Context.MODE_PRIVATE).getBoolean(ServerService.PREF_RUN, false)
    if (startsAgain(intent.action, run)) ServerService.start(context)
  }

  companion object {
    /** Pure, so which broadcasts start it is tested. */
    fun startsAgain(action: String?, wasRunning: Boolean): Boolean =
      wasRunning && (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED)
  }
}
