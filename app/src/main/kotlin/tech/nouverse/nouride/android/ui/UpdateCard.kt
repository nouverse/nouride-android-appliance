package tech.nouverse.nouride.android.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import tech.nouverse.nouride.android.Layout
import tech.nouverse.nouride.android.R
import tech.nouverse.nouride.android.server.ServerState
import tech.nouverse.nouride.android.update.Release
import tech.nouverse.nouride.android.update.Updates
import kotlin.concurrent.thread

/**
 * A new version of Nouride Home, on the status screen. The first time a release is found, a card over the screen says so
 * (Update now, or Later); after that a small card stays at the top until it is installed, and the notification says
 * it too (`ServerService`). Update downloads it with its progress, checks it, and hands it to Android's installer, which
 * asks once. Words only: no version code, no sha, no URL. All of it waits while the first start sets Nouride up
 * (`Updates.waitsFor`), and comes once it is done.
 */
internal class UpdateCard(
  private val activity: Activity,
  private val ui: Ui,
  /** After each check of the feed: the More tab's line says what it found too, not what it knew before. */
  private val onChecked: () -> Unit = {},
) {
  private enum class State { IDLE, DOWNLOADING, INSTALLING, FAILED }

  val view: LinearLayout = ui.card(fill = ui.color(R.color.brand_soft)).apply { visibility = View.GONE }
  private val title: TextView = ui.text(17f, R.color.ink, bold = true).apply { setText(R.string.update_title) }
  private val body: TextView = ui.text(15f, R.color.muted)
  private val bar: ProgressBar = ui.progressBar()
  private val button: Button = ui.primary { start() }
  private var release: Release? = null
  private var state = State.IDLE
  private var percent = 0
  private var askedToAllow = false
  private val layout = Layout(activity)
  private var startPressed = false
  private var offerDialog: Dialog? = null
  private var settingUp = waits()

  init {
    view.addView(title)
    view.addView(body, ui.fullWidth(6))
    view.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(12)).apply { topMargin = ui.dp(14) })
    view.addView(button, ui.fullWidth(14))
  }

  /** On the screen's resume: what is known at once, then a check of the feed if the last answer is old. */
  fun resume() {
    // The state may have moved while the screen was away, when nothing told this card.
    settingUp = waits()
    // Back from Android's installer and still this version: it was cancelled or failed, so Update is offered again.
    if (state == State.INSTALLING) state = State.IDLE
    if (askedToAllow && Updates.canInstall(activity)) {
      askedToAllow = false
      show(Updates.waiting(activity))
      start()
      return
    }
    show(Updates.waiting(activity))
    thread(name = "update-check", isDaemon = true) {
      val found = Updates.check(activity, Updates.ON_OPEN_MS)
      activity.runOnUiThread {
        if (activity.isDestroyed) return@runOnUiThread
        show(found)
        onChecked()
        offerOnce()
      }
    }
  }

  /** From the status screen at every change of the server's state: the end of the first start brings the card. */
  fun serverChanged() {
    val now = waits()
    if (now == settingUp) return
    settingUp = now
    if (now) offerDialog?.dismiss()
    show(release)
    offerOnce()
  }

  /** From the status screen when the person presses Start (or `--ez start true`), before the service has said a word. */
  fun starting() {
    startPressed = true
    serverChanged()
  }

  private fun waits() = Updates.waitsFor(ServerState.phase, startPressed) { layout.installed }

  private fun show(found: Release?) {
    if (state == State.DOWNLOADING || state == State.INSTALLING) return
    release = found
    view.visibility = if (found == null || settingUp) View.GONE else View.VISIBLE
    render()
  }

  /** The big card, once a release, and not while Nouride is being set up: it comes when that is done. */
  private fun offerOnce() {
    val found = release ?: return
    if (settingUp || Updates.offered(activity, found.version)) return
    Updates.markOffered(activity, found.version)
    offer(found)
  }

  private fun render() {
    val found = release ?: return
    bar.visibility = if (state == State.DOWNLOADING) View.VISIBLE else View.GONE
    bar.isIndeterminate = percent == 0
    bar.progress = percent
    button.visibility = if (state == State.IDLE || state == State.FAILED) View.VISIBLE else View.GONE
    button.setText(if (state == State.FAILED || Updates.failed(activity)) R.string.update_retry else R.string.update_button)
    body.text = when {
      state == State.DOWNLOADING -> activity.getString(R.string.update_downloading, percent)
      state == State.INSTALLING -> activity.getString(R.string.update_installing)
      state == State.FAILED -> activity.getString(R.string.update_failed)
      Updates.failed(activity) -> activity.getString(R.string.update_install_failed)
      else -> activity.getString(R.string.update_body, found.version)
    }
  }

  private fun start() {
    val found = release ?: return
    if (settingUp) return
    if (!Updates.canInstall(activity)) return askToAllow()
    state = State.DOWNLOADING
    percent = 0
    render()
    thread(name = "update-download", isDaemon = true) {
      try {
        val apk = Updates.download(activity, found) { done ->
          activity.runOnUiThread {
            percent = done
            if (!activity.isDestroyed) render()
          }
        }
        activity.runOnUiThread {
          // Start pressed while it downloaded: the setup goes first, and Update is there again after it.
          if (waits()) {
            state = State.IDLE
            show(release)
            return@runOnUiThread
          }
          state = State.INSTALLING
          render()
          try {
            Updates.install(activity, apk)
          } catch (e: Exception) {
            state = State.FAILED
            render()
          }
        }
      } catch (e: Exception) {
        activity.runOnUiThread {
          state = State.FAILED
          if (!activity.isDestroyed) render()
        }
      }
    }
  }

  /** Android 8 and later ask the person, once, to let this app install its own updates. */
  private fun askToAllow() {
    card(R.drawable.mascot_hello, activity.getString(R.string.update_allow_title), activity.getString(R.string.update_allow_body),
      R.string.update_allow) {
      askedToAllow = true
      activity.startActivity(Updates.allowInstallIntent(activity))
    }
  }

  private fun offer(found: Release) {
    offerDialog = card(R.drawable.mascot_ride, activity.getString(R.string.update_dialog_title),
      activity.getString(R.string.update_dialog_body, found.version), R.string.update_now) { start() }
  }

  /** A round card over the screen, as "Use from another device" is: the cat, a title, a sentence, one button and Later. */
  private fun card(image: Int, heading: String, sentence: String, action: Int, onAction: () -> Unit): Dialog {
    val dialog = Dialog(activity)
    val content = ui.card(padding = 24).apply { gravity = Gravity.CENTER_HORIZONTAL }
    content.addView(ImageView(activity).apply {
      setImageResource(image)
      adjustViewBounds = true
    }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, ui.dp(120)))
    content.addView(ui.text(20f, R.color.ink, bold = true, center = true).apply { text = heading }, ui.fullWidth(12))
    content.addView(ui.text(15f, R.color.muted, center = true).apply { text = sentence }, ui.fullWidth(6))
    content.addView(ui.primary {
      dialog.dismiss()
      onAction()
    }.apply { setText(action) }, ui.fullWidth(20))
    content.addView(ui.quiet { dialog.dismiss() }.apply { setText(R.string.later) }, ui.fullWidth(4))
    dialog.setContentView(content)
    dialog.window?.apply {
      setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
      setLayout(minOf(activity.resources.displayMetrics.widthPixels - ui.dp(32), ui.dp(420)), ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    dialog.show()
    return dialog
  }
}
