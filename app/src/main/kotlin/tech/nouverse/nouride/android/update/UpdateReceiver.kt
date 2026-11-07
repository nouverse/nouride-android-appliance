package tech.nouverse.nouride.android.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import tech.nouverse.nouride.android.server.ServerService

/**
 * Android's installer reporting on an update `Updates.install` handed it: first that it needs the person's yes, with the
 * screen that asks, then how it ended. The success is heard by the new version, which starts Nouride again if it was
 * running: Android stopped it to replace the app, and HyperOS keeps `MY_PACKAGE_REPLACED` (`BootReceiver`) from an app
 * not allowed to autostart, which left Nouride off until the app was opened. This report is the app's own, so it comes.
 */
class UpdateReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != ACTION) return
    val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
    if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
      @Suppress("DEPRECATION")
      val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
      context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
      return
    }
    Updates.setFailed(context, status != PackageInstaller.STATUS_SUCCESS)
    val run = context.getSharedPreferences(ServerService.PREFS, Context.MODE_PRIVATE).getBoolean(ServerService.PREF_RUN, false)
    if (startsAgain(status, run)) ServerService.start(context)
  }

  companion object {
    const val ACTION = "tech.nouverse.nouride.android.UPDATE_STATUS"

    /** Pure, so when it starts Nouride is tested: after an update that went in, if Nouride was running. */
    fun startsAgain(status: Int, wasRunning: Boolean): Boolean = wasRunning && status == PackageInstaller.STATUS_SUCCESS
  }
}
