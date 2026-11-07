package tech.nouverse.nouride.android.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import tech.nouverse.nouride.android.Layout
import tech.nouverse.nouride.android.Nouride
import tech.nouverse.nouride.android.R
import tech.nouverse.nouride.android.power.PowerSettings
import tech.nouverse.nouride.android.server.ServerService
import tech.nouverse.nouride.android.server.ServerState
import tech.nouverse.nouride.android.server.Step

/**
 * The one screen a person uses: the cat, one sentence of how Nouride is, and at most one big button. The users are not
 * technical, so the server's phases, addresses and log stay on a screen of their own, "Details for support", behind a
 * quiet link at the bottom (`SupportActivity`, `StatusLook`, and the android-appliance-ui skill). The boolean extras `start`, `stop` and `dashboard` do what the
 * buttons do, for `adb shell am start --ez <name> true`.
 */
class StatusActivity : Activity() {
  private lateinit var ui: Ui
  private lateinit var layout: Layout
  private lateinit var hero: LinearLayout
  private lateinit var mascot: ImageView
  private lateinit var title: TextView
  private lateinit var body: TextView
  private lateinit var progress: ProgressBar
  private lateinit var stepBox: LinearLayout
  private lateinit var stepText: TextView
  private lateinit var stepCount: TextView
  private lateinit var stepPercent: TextView
  private lateinit var primary: Button
  private lateinit var share: Button
  private lateinit var battery: LinearLayout
  private lateinit var turnOff: Button
  private lateinit var update: UpdateCard
  private var bob: ObjectAnimator? = null
  private var shownMascot: Mascot? = null
  private val handler = Handler(Looper.getMainLooper())
  private val listener: () -> Unit = {
    render()
    update.serverChanged()
  }
  private val refresh = object : Runnable {
    override fun run() {
      render()
      handler.postDelayed(this, 2_000)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    ui = Ui(this)
    layout = Layout(this)
    ui.lightSystemBars(window)
    setContentView(views())
    if (savedInstanceState == null) handleExtras(intent)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleExtras(intent)
  }

  private fun handleExtras(intent: Intent) {
    if (intent.getBooleanExtra(EXTRA_START, false)) startServer()
    if (intent.getBooleanExtra(EXTRA_STOP, false)) ServerService.stop(this)
    if (intent.getBooleanExtra(EXTRA_DASHBOARD, false)) openDashboard()
  }

  override fun onResume() {
    super.onResume()
    ServerState.listen(listener)
    handler.post(refresh)
    update.resume()
  }

  override fun onPause() {
    ServerState.unlisten(listener)
    handler.removeCallbacks(refresh)
    super.onPause()
  }

  private fun startServer() {
    update.starting()
    ServerService.start(this)
    if (!PowerSettings.isExempt(this)) startActivity(PowerSettings.exemptionRequest(this))
  }

  private fun openDashboard() = startActivity(Intent(this, DashboardActivity::class.java))

  private fun views(): View {
    val column = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(32))
    }
    column.addView(header())
    update = UpdateCard(this, ui)
    column.addView(update.view, ui.fullWidth(12))

    hero = ui.card(padding = 24).apply { gravity = Gravity.CENTER_HORIZONTAL }
    mascot = ImageView(this).apply { adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER }
    hero.addView(mascot, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, ui.dp(220)))
    title = ui.text(24f, R.color.ink, bold = true, center = true)
    hero.addView(title, ui.fullWidth(16))
    body = ui.text(16f, R.color.muted, center = true)
    hero.addView(body, ui.fullWidth(8))
    progress = ui.progressBar()
    hero.addView(progress, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(12)).apply {
      topMargin = ui.dp(20)
    })
    hero.addView(stepRow(), ui.fullWidth(10))
    primary = ui.primary { onPrimary() }
    hero.addView(primary, ui.fullWidth(20))
    column.addView(hero, ui.fullWidth(12))

    share = ui.secondary { showAddress() }.apply { setText(R.string.other_device) }
    column.addView(share, ui.fullWidth(12))

    battery = ui.card(fill = ui.color(R.color.warn_soft)).apply {
      addView(ui.text(17f, R.color.ink, bold = true).apply { setText(R.string.battery_title) })
      addView(ui.text(15f, R.color.muted).apply { setText(R.string.battery_body) }, ui.fullWidth(6))
      addView(ui.secondary { startActivity(PowerSettings.exemptionRequest(this@StatusActivity)) }.apply {
        setText(R.string.battery_allow)
      }, ui.fullWidth(12))
      addView(ui.quiet { startActivity(PowerSettings.appDetails(this@StatusActivity)) }.apply {
        setText(R.string.battery_settings)
      }, ui.fullWidth(4))
    }
    column.addView(battery, ui.fullWidth(12))

    turnOff = ui.quiet { confirmTurnOff() }.apply { setText(R.string.turn_off) }
    column.addView(turnOff, ui.fullWidth(8))

    column.addView(ui.quiet { startActivity(Intent(this, SupportActivity::class.java)) }.apply {
      setText(R.string.details)
      textSize = 13f
    }, ui.fullWidth(24))
    return ScrollView(this).apply {
      setBackgroundColor(ui.color(R.color.background))
      addView(column)
    }
  }

  /** Under the bar on the first start: what is happening now in plain words, the step, and the percent. */
  private fun stepRow(): View {
    stepBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    stepText = ui.text(15f, R.color.ink, bold = true, center = true)
    stepBox.addView(stepText, ui.fullWidth())
    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    stepCount = ui.text(13f, R.color.muted)
    row.addView(stepCount, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    stepPercent = ui.text(13f, R.color.brand, bold = true)
    row.addView(stepPercent)
    stepBox.addView(row, ui.fullWidth(4))
    return stepBox
  }

  private fun header(): View = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    addView(ImageView(this@StatusActivity).apply {
      setImageResource(R.drawable.icon_cat)
      background = ui.rounded(ui.color(R.color.icon_background), 18)
      clipToOutline = true
    }, LinearLayout.LayoutParams(ui.dp(36), ui.dp(36)))
    addView(ui.text(18f, R.color.ink, bold = true).apply {
      setText(R.string.app_name)
      setPadding(ui.dp(10), 0, 0, 0)
    })
  }

  private fun render() {
    val health = ServerState.health
    val look = StatusLook.of(ServerState.phase, answering = health?.ok == true)
    if (look.mascot != shownMascot) {
      mascot.setImageResource(look.mascot.drawable())
      shownMascot = look.mascot
    }
    bobbing(look.busy)
    hero.background = ui.rounded(ui.color(look.tone.fill()), 28, ui.color(R.color.line))
    title.setText(look.words.title())
    body.setText(look.words.body())
    progress.visibility = if (look.busy) View.VISIBLE else View.GONE
    // A real percent on the first start; any later start is seconds long, and only says it is busy.
    val setup = ServerState.progress?.takeIf { look.busy }
    progress.isIndeterminate = setup == null
    stepBox.visibility = if (setup == null) View.GONE else View.VISIBLE
    if (setup != null) {
      progress.setProgress(setup.percent, true)
      stepText.setText(setup.step.label())
      stepCount.text = getString(R.string.step_count, setup.step.ordinal + 1, Step.values().size)
      stepPercent.text = getString(R.string.percent, setup.percent)
    }
    val label = look.action.label()
    primary.visibility = if (label == null) View.GONE else View.VISIBLE
    if (label != null) primary.setText(label)
    share.visibility = if (StatusLook.canShare(look)) View.VISIBLE else View.GONE
    battery.visibility = if (PowerSettings.isExempt(this)) View.GONE else View.VISIBLE
    turnOff.visibility = if (StatusLook.canTurnOff(ServerState.phase)) View.VISIBLE else View.GONE
  }

  private fun onPrimary() = when (StatusLook.of(ServerState.phase, ServerState.health?.ok == true).action) {
    Action.START, Action.RETRY -> startServer()
    Action.OPEN -> openDashboard()
    Action.NONE -> Unit
  }

  /** The cat floats a little while something is happening, and stands still when it is done. */
  private fun bobbing(on: Boolean) {
    if (on && bob == null) {
      bob = ObjectAnimator.ofFloat(mascot, View.TRANSLATION_Y, 0f, -ui.dp(8).toFloat()).apply {
        duration = 900
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        start()
      }
    } else if (!on && bob != null) {
      bob?.cancel()
      bob = null
      mascot.translationY = 0f
    }
  }

  /**
   * The address for another device, in a card of its own: the cat, one sentence, the address large, and Share as the
   * main button, since the address is usually sent to the computer rather than typed from the phone's screen.
   */
  private fun showAddress() {
    val lan = SupportText.lanAddress()
    val dialog = Dialog(this)
    val card = ui.card(padding = 24).apply { gravity = Gravity.CENTER_HORIZONTAL }
    card.addView(ImageView(this).apply {
      setImageResource(if (lan == null) R.drawable.mascot_lean else R.drawable.mascot_wave)
      adjustViewBounds = true
    }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, ui.dp(120)))
    card.addView(ui.text(20f, R.color.ink, bold = true, center = true).apply {
      setText(if (lan == null) R.string.no_wifi_title else R.string.other_device_hint)
    }, ui.fullWidth(12))
    card.addView(ui.text(15f, R.color.muted, center = true).apply {
      setText(if (lan == null) R.string.no_wifi else R.string.other_device_body)
    }, ui.fullWidth(6))
    if (lan == null) {
      card.addView(ui.primary { dialog.dismiss() }.apply { setText(R.string.got_it) }, ui.fullWidth(20))
    } else {
      val address = "http://$lan:${Nouride.PORT}"
      card.addView(ui.text(22f, R.color.brand, bold = true, center = true).apply {
        text = address
        setTextIsSelectable(true)
        maxLines = 1
        if (Build.VERSION.SDK_INT >= 26) setAutoSizeTextTypeUniformWithConfiguration(14, 22, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
        background = ui.rounded(ui.color(R.color.brand_soft), 20)
        setPadding(ui.dp(12), ui.dp(16), ui.dp(12), ui.dp(16))
      }, ui.fullWidth(18))
      card.addView(ui.primary {
        dialog.dismiss()
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, address), null))
      }.apply { setText(R.string.share_address) }, ui.fullWidth(20))
      card.addView(ui.secondary { copy(address) }.apply { setText(R.string.copy) }, ui.fullWidth(10))
      card.addView(ui.quiet { dialog.dismiss() }.apply { setText(R.string.close) }, ui.fullWidth(4))
    }
    dialog.setContentView(card)
    dialog.window?.apply {
      setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
      val width = minOf(resources.displayMetrics.widthPixels - ui.dp(32), ui.dp(420))
      setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    dialog.show()
  }

  private fun confirmTurnOff() {
    AlertDialog.Builder(this)
      .setTitle(R.string.turn_off_title)
      .setMessage(R.string.turn_off_body)
      .setPositiveButton(R.string.turn_off) { _, _ -> ServerService.stop(this) }
      .setNegativeButton(R.string.cancel, null)
      .show()
  }

  private fun copy(text: String) {
    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), text))
    Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
  }

  companion object {
    const val EXTRA_START = "start"
    const val EXTRA_STOP = "stop"
    const val EXTRA_DASHBOARD = "dashboard"
  }
}
