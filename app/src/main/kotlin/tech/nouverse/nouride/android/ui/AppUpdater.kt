package tech.nouverse.nouride.android.ui

import android.app.Activity
import tech.nouverse.nouride.android.update.Release
import tech.nouverse.nouride.android.update.Updates
import kotlin.concurrent.thread

/**
 * Downloading a new Nouride Home and handing it to Android's installer, wherever Update is pressed: the card on Home
 * (`UpdateCard`) and the Versions screen. Each draws it its own way from [state] and [percent], on [onChange].
 */
internal class AppUpdater(
  private val activity: Activity,
  /** Whether the first start is setting Nouride up, when an update waits (`Updates.waitsFor`). */
  private val waits: () -> Boolean,
  private val onChange: () -> Unit,
  /** Android 8 and later ask the person once to let this app install its updates: the screen says so, then [allowing]. */
  private val askToAllow: () -> Unit,
) {
  enum class State { IDLE, DOWNLOADING, INSTALLING, FAILED }

  var state = State.IDLE
    private set
  var percent = 0
    private set
  private var askedToAllow = false

  val busy: Boolean get() = state == State.DOWNLOADING || state == State.INSTALLING

  /**
   * On the screen's resume. Back from Android's installer and still this version: it was cancelled or failed, so Update
   * is offered again. True when the person has just allowed installs in Settings: the caller goes on with [start].
   */
  fun resume(): Boolean {
    if (state == State.INSTALLING) state = State.IDLE
    if (!askedToAllow || !Updates.canInstall(activity)) return false
    askedToAllow = false
    return true
  }

  /** The person pressed the button that opens Settings, where they allow it. */
  fun allowing() {
    askedToAllow = true
    activity.startActivity(Updates.allowInstallIntent(activity))
  }

  fun start(release: Release) {
    if (busy || waits()) return
    if (!Updates.canInstall(activity)) return askToAllow()
    state = State.DOWNLOADING
    percent = 0
    onChange()
    thread(name = "update-download", isDaemon = true) {
      try {
        val apk = Updates.download(activity, release) { done ->
          activity.runOnUiThread {
            percent = done
            if (!activity.isDestroyed) onChange()
          }
        }
        activity.runOnUiThread {
          // Start pressed while it downloaded: the setup goes first, and Update is there again after it.
          if (waits()) {
            state = State.IDLE
            onChange()
            return@runOnUiThread
          }
          state = State.INSTALLING
          onChange()
          try {
            Updates.install(activity, apk)
          } catch (e: Exception) {
            state = State.FAILED
            onChange()
          }
        }
      } catch (e: Exception) {
        activity.runOnUiThread {
          state = State.FAILED
          if (!activity.isDestroyed) onChange()
        }
      }
    }
  }
}
