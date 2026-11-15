package tech.nouverse.nouride.android.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import tech.nouverse.nouride.android.server.Place
import tech.nouverse.nouride.android.server.ServerService
import tech.nouverse.nouride.android.server.Where

/**
 * Starts the server again after the phone boots, and after this app is updated (Android stops an app to replace it),
 * if it was running then: an update of Nouride Home must not leave the person's agents off until they open the app.
 * HyperOS may hold either back from an app it does not let autostart (`PowerSettings.asksForAutostart` says which it did
 * on one phone); an update from the app itself is started again by `UpdateReceiver` either way.
 */
class BootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val run = context.getSharedPreferences(ServerService.PREFS, Context.MODE_PRIVATE).getBoolean(ServerService.PREF_RUN, false)
    val remote = Place(context).where() == Where.REMOTE
    if (startsAgain(intent.action, run, remote)) ServerService.start(context)
  }

  companion object {
    /**
     * Pure, so which broadcasts start it is tested. Never while Nouride lives on another machine: this phone runs no
     * server, and starting one would be a Nouride nobody asked for, on a phone that was told to open someone else's.
     */
    fun startsAgain(action: String?, wasRunning: Boolean, remote: Boolean = false): Boolean =
      !remote && wasRunning && (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED)
  }
}
