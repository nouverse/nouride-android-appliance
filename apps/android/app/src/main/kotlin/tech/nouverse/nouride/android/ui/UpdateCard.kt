package tech.nouverse.nouride.android.ui

import android.app.Activity
import android.view.View
import android.widget.Button
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
 * A new version of Nouride Home, on Home: a small card at the top until it is installed, and the notification says it
 * too (`ServerService`). Nothing comes over the screen for it: an update is not a question the person has to answer
 * now. Update downloads it with its progress, checks it, and hands it to Android's installer, which asks once. When
 * Android does not let this app install yet, the card says so with Allow, which opens that setting, and the update goes
 * on when the person comes back (`AppUpdater.allowing`). Words only: no version code, no sha, no URL. All of it waits
 * while the first start sets Nouride up (`Updates.waitsFor`), and comes once it is done.
 */
internal class UpdateCard(
  private val activity: Activity,
  private val ui: Ui,
  /** After each check of the feed: the More tab's Versions line says what it found too, not what it knew before. */
  private val onChecked: () -> Unit = {},
) {
  val view: LinearLayout = ui.card(fill = ui.color(R.color.brand_soft)).apply { visibility = View.GONE }
  private val title: TextView = ui.text(17f, R.color.ink, bold = true).apply { setText(R.string.update_title) }
  private val body: TextView = ui.text(15f, R.color.muted)
  private val bar: ProgressBar = ui.progressBar()
  private var look = UpdateLooks.of(UpdateStep.READY)
  private val button: Button = ui.primary { pressed() }
  private var release: Release? = null
  private val layout = Layout(activity)
  private var startPressed = false
  private var settingUp = waits()
  // Done, or waiting for the setup again: the card is shown or hidden for what is known now, as `show` decides.
  private val updater: AppUpdater = AppUpdater(activity, ::waits, onChange = { if (updater.busy) render() else show(release) })

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
    if (updater.resume()) {
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
      }
    }
  }

  /** From the status screen at every change of the server's state: the end of the first start brings the card. */
  fun serverChanged() {
    val now = waits()
    if (now == settingUp) return
    settingUp = now
    show(release)
  }

  /** From the status screen when the person presses Start (or `--ez start true`), before the service has said a word. */
  fun starting() {
    startPressed = true
    serverChanged()
  }

  private fun waits() = Updates.waitsFor(ServerState.phase, startPressed) { layout.installed }

  private fun show(found: Release?) {
    if (updater.busy) return
    release = found
    view.visibility = if (found == null || settingUp) View.GONE else View.VISIBLE
    render()
  }

  private fun render() {
    val found = release ?: return
    look = UpdateLooks.of(UpdateLooks.step(updater.state, Updates.canInstall(activity), Updates.failed(activity)))
    bar.visibility = if (look.progress) View.VISIBLE else View.GONE
    bar.isIndeterminate = updater.percent == 0
    bar.progress = updater.percent
    button.visibility = if (look.button == UpdateButton.NONE) View.GONE else View.VISIBLE
    if (look.label != 0) button.setText(look.label)
    body.text = activity.getString(look.words, found.version, updater.percent)
  }

  private fun pressed() = when (look.button) {
    UpdateButton.UPDATE -> start()
    UpdateButton.ALLOW -> updater.allowing()
    UpdateButton.NONE -> Unit
  }

  private fun start() {
    val found = release ?: return
    if (settingUp) return
    updater.start(found)
  }
}
