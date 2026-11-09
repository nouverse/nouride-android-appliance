package tech.nouverse.nouride.android.ui

import android.app.Activity
import android.os.SystemClock
import tech.nouverse.nouride.android.R
import tech.nouverse.nouride.android.server.ServerState
import tech.nouverse.nouride.android.update.NourideDownload
import tech.nouverse.nouride.android.update.NourideUpdates
import tech.nouverse.nouride.android.update.StallWatch
import java.io.IOException
import kotlin.concurrent.thread

/**
 * Nouride's version and its update, on the Versions screen: the dashboard's Update page in one press, through the same
 * routes (`NourideUpdates`). Update downloads the new version with its progress and puts it in; Nouride restarts by
 * itself, and the card follows it back to "You have the newest version". Off, the card says to turn Nouride on.
 */
internal class NourideCard(private val activity: Activity, ui: Ui) {
  private val card = VersionCard(activity, ui, R.string.versions_nouride, ::pressed)
  val view get() = card.view
  private var step = NourideStep.OFF
  private var installed: String? = null
  private var next: String? = null
  private var percent = 0

  /** A call is out, or a download is followed: nothing else starts. Read and written on the main thread only. */
  private var working = false

  /** Nouride stopped answering after the new version went in: the next answer is the new version's. */
  private var wentAway = false

  @Volatile
  private var closed = false

  /** On the screen's resume: Nouride asked again, without reaching past its cache of the release channel. */
  fun resume() = check(force = false)

  /** On the screen's close: a download goes on in Nouride, and the card picks it up when the screen opens again. */
  fun close() {
    closed = true
  }

  /** At every change of the app's state: Nouride turned off, back on, or answering again after its update. */
  fun serverChanged() {
    if (!answering()) {
      // Restarting into the new version is part of the update: the card says so until Nouride answers again.
      if (step in RESTARTING) wentAway = true else if (!working) show(NourideStep.OFF)
      return
    }
    val now = ServerState.health?.version?.removePrefix("v")
    if (step == NourideStep.OFF || (step in RESTARTING && (wentAway || (now != null && now != installed)))) {
      wentAway = false
      installed = now ?: installed
      check(force = false)
    }
  }

  private fun answering() = ServerState.phase == ServerState.Phase.RUNNING && ServerState.health?.ok == true

  private fun pressed(button: VersionButton) = when (button) {
    VersionButton.CHECK -> check(force = true)
    VersionButton.UPDATE -> update(download = true)
    VersionButton.INSTALL -> update(download = false)
    VersionButton.NONE -> Unit
  }

  private fun check(force: Boolean) {
    if (working) return
    if (!answering()) return show(NourideStep.OFF)
    working = true
    installed = installed ?: ServerState.health?.version?.removePrefix("v")
    show(NourideStep.CHECKING)
    thread(name = "nouride-update-check", isDaemon = true) {
      val status = try {
        NourideUpdates.status(activity, force)
      } catch (e: Exception) {
        return@thread done(NourideStep.CHECK_FAILED)
      }
      ui {
        installed = status.current ?: installed
        next = status.ready
        percent = status.download.percent
        val now = VersionLooks.nourideStep(status)
        show(now)
        if (now != NourideStep.DOWNLOADING) {
          working = false
          return@ui
        }
        // Started on the dashboard, or before this screen was closed: followed to its end, then offered with Install.
        thread(name = "nouride-update-progress", isDaemon = true) {
          try {
            follow(install = false)
          } catch (e: Exception) {
            done(NourideStep.DOWNLOAD_FAILED)
          }
        }
      }
    }
  }

  /** [download]: fetch it first; without, put in the version already fetched. */
  private fun update(download: Boolean) {
    if (working || !answering()) return
    working = true
    percent = 0
    show(if (download) NourideStep.DOWNLOADING else NourideStep.INSTALLING)
    thread(name = "nouride-update", isDaemon = true) {
      try {
        if (download) {
          NourideUpdates.download(activity)
          follow(install = true)
        } else {
          install()
        }
      } catch (e: Exception) {
        done(if (download) NourideStep.DOWNLOAD_FAILED else NourideStep.INSTALL_FAILED)
      }
    }
  }

  /**
   * Blocking. The download once a second to its end; then put in when [install], else offered with Install. One that
   * stops moving is paused and resumed on a new connection (`StallWatch`).
   */
  private fun follow(install: Boolean) {
    val watch = StallWatch(STALL_MS, MAX_RESTARTS)
    while (!closed) {
      val download = NourideUpdates.progress(activity)
      ui {
        percent = download.percent
        show(NourideStep.DOWNLOADING)
      }
      when (download.status) {
        NourideDownload.Status.READY -> break
        NourideDownload.Status.DOWNLOADING -> when (watch.seen(download.received, SystemClock.elapsedRealtime())) {
          StallWatch.Verdict.MOVING -> Unit
          StallWatch.Verdict.RESTART -> {
            NourideUpdates.pause(activity)
            NourideUpdates.download(activity)
          }
          StallWatch.Verdict.GIVE_UP -> throw IOException("the download stopped moving")
        }
        // Failed, or paused or thrown away on the dashboard: this download is over either way.
        else -> throw IOException("the download stopped: ${download.status}")
      }
      Thread.sleep(PROGRESS_EVERY_MS)
    }
    if (closed) return
    if (install) install() else done(NourideStep.DOWNLOADED)
  }

  /** Blocking. Its own failure is said as one: by now the download is done. */
  private fun install() {
    ui { show(NourideStep.INSTALLING) }
    val restart = try {
      NourideUpdates.install(activity)
    } catch (e: Exception) {
      return done(NourideStep.INSTALL_FAILED)
    }
    done(VersionLooks.restarting(restart))
  }

  /** From any thread: the work is over, at [last]. */
  private fun done(last: NourideStep) = ui {
    working = false
    show(last)
  }

  private fun show(now: NourideStep) {
    step = now
    val look = VersionLooks.nouride(now)
    card.render(look, if (now == NourideStep.OFF) null else installed, next, if (look.progress) percent else 0)
  }

  private fun ui(block: () -> Unit) = activity.runOnUiThread { if (!activity.isDestroyed) block() }

  private companion object {
    const val PROGRESS_EVERY_MS = 1_000L

    /** 57 MB on a phone's Wi-Fi took 16 seconds: half a minute without a byte is a connection that went quiet. */
    const val STALL_MS = 30_000L
    const val MAX_RESTARTS = 5
    val RESTARTING = setOf(NourideStep.RESTARTING_NOW, NourideStep.RESTARTING_IDLE, NourideStep.RESTARTING_JOBS)
  }
}
