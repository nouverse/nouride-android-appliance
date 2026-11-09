package tech.nouverse.nouride.android.ui

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import tech.nouverse.nouride.android.Layout
import tech.nouverse.nouride.android.R
import tech.nouverse.nouride.android.server.ServerState
import tech.nouverse.nouride.android.update.Release
import tech.nouverse.nouride.android.update.Updates
import java.io.IOException
import kotlin.concurrent.thread

/**
 * "Versions and updates", opened from More: which version of Nouride Home this is and which of Nouride runs, each with
 * Check for updates and, when there is a newer one, its update in one press. The app's update is the one Home offers
 * (`AppUpdater`); Nouride's is the one its dashboard offers (`NourideCard`).
 */
class VersionsActivity : Activity() {
  private lateinit var ui: Ui
  private lateinit var layout: Layout
  private lateinit var app: VersionCard
  private lateinit var nouride: NourideCard
  private lateinit var updater: AppUpdater

  /** What the last press of Check for updates found; the release itself is in `Updates`. */
  private var appStep = AppStep.UP_TO_DATE
  private val listener: () -> Unit = {
    nouride.serverChanged()
    showApp()
  }

  /** In the appearance chosen on the More tab (`Appearances`). */
  override fun attachBaseContext(base: Context) = super.attachBaseContext(Appearances.wrap(base))

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    ui = Ui(this)
    layout = Layout(this)
    ui.lightSystemBars(window)
    app = VersionCard(this, ui, R.string.app_name, ::appPressed)
    nouride = NourideCard(this, ui)
    updater = AppUpdater(this, ::waits, onChange = ::showApp) { askToAllowInstalls(this, ui) { updater.allowing() } }
    setContentView(views())
  }

  override fun onResume() {
    super.onResume()
    ServerState.listen(listener)
    if (updater.resume()) Updates.waiting(this)?.let(updater::start)
    showApp()
    nouride.resume()
  }

  override fun onPause() {
    ServerState.unlisten(listener)
    super.onPause()
  }

  override fun onDestroy() {
    nouride.close()
    super.onDestroy()
  }

  private fun views(): View {
    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      background = ui.page()
    }
    root.addView(LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      setPadding(ui.dp(8), ui.dp(8), ui.dp(16), ui.dp(8))
      minimumHeight = ui.dp(56)
      addView(ImageView(context).apply {
        setImageResource(R.drawable.ic_back)
        contentDescription = getString(R.string.close)
        setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8))
        setOnClickListener { finish() }
      }, LinearLayout.LayoutParams(ui.dp(44), ui.dp(44)))
      addView(ui.text(18f, R.color.ink, bold = true).apply {
        setText(R.string.versions_title)
        setPadding(ui.dp(6), 0, 0, 0)
      })
    })
    val column = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(ui.dp(20), ui.dp(4), ui.dp(20), ui.dp(24))
      addView(app.view, ui.fullWidth())
      addView(nouride.view, ui.fullWidth(12))
    }
    root.addView(ScrollView(this).apply { addView(column) }, LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f,
    ))
    return root
  }

  private fun waits() = Updates.waitsFor(ServerState.phase, startPressed = false) { layout.installed }

  private fun appPressed(button: VersionButton) {
    when (button) {
      VersionButton.CHECK -> checkApp()
      VersionButton.UPDATE -> Updates.waiting(this)?.let(updater::start)
      VersionButton.INSTALL, VersionButton.NONE -> Unit
    }
  }

  /** The feed asked now, however fresh the last answer, as More's Check for updates did. */
  private fun checkApp() {
    if (appStep == AppStep.CHECKING || updater.busy) return
    appStep = AppStep.CHECKING
    showApp()
    thread(name = "update-check-now", isDaemon = true) {
      val failed = try {
        Updates.checkNow(this)
        false
      } catch (e: IOException) {
        true
      }
      runOnUiThread {
        if (isDestroyed) return@runOnUiThread
        appStep = if (failed) AppStep.CHECK_FAILED else AppStep.UP_TO_DATE
        showApp()
      }
    }
  }

  /** The app's card, from what the updater is doing and what the last check of the feed found. */
  private fun showApp() {
    val waiting: Release? = Updates.waiting(this)
    val step = when {
      updater.state == AppUpdater.State.DOWNLOADING -> AppStep.DOWNLOADING
      updater.state == AppUpdater.State.INSTALLING -> AppStep.INSTALLING
      appStep == AppStep.CHECKING -> AppStep.CHECKING
      updater.state == AppUpdater.State.FAILED && waiting != null -> AppStep.DOWNLOAD_FAILED
      Updates.failed(this) && waiting != null -> AppStep.INSTALL_FAILED
      // While Nouride is first set up, Update waits for it, as on Home (`AppUpdater.start`).
      waiting != null -> AppStep.READY
      appStep == AppStep.CHECK_FAILED -> AppStep.CHECK_FAILED
      else -> AppStep.UP_TO_DATE
    }
    val look = VersionLooks.app(step)
    app.render(look, Updates.installedVersion(this), waiting?.version, updater.percent)
  }
}
