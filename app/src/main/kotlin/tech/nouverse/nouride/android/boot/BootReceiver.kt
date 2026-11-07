package tech.nouverse.nouride.android.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import tech.nouverse.nouride.android.server.ServerService

/** Starts the server after the phone boots if it was running when the phone went down. */
class BootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
    val run = context.getSharedPreferences(ServerService.PREFS, Context.MODE_PRIVATE).getBoolean(ServerService.PREF_RUN, false)
    if (run) ServerService.start(context)
  }
}
