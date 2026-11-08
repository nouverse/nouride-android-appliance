package tech.nouverse.nouride.android.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import tech.nouverse.nouride.android.R
import tech.nouverse.nouride.android.server.ServerState
import tech.nouverse.nouride.android.server.Step

/**
 * The Home tab, the one a person needs: the cat, one sentence of how Nouride is, and at most one big button. Above it
 * a new version of the app when there is one (`UpdateCard`); under it, only while the phone may stop Nouride, one line
 * that leads to More, where that is fixed.
 */
internal class HomeTab(
  private val context: Context,
  private val ui: Ui,
  update: UpdateCard,
  private val onPrimary: () -> Unit,
  private val onKeepRunning: () -> Unit,
) {
  private val hero: LinearLayout = ui.card(padding = 24).apply { gravity = Gravity.CENTER_HORIZONTAL }
  private val mascot = ImageView(context).apply { adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER }
  private val title: TextView = ui.text(24f, R.color.ink, bold = true, center = true)
  private val body: TextView = ui.text(16f, R.color.muted, center = true)
  private val progress: ProgressBar = ui.progressBar()
  private val stepBox = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
  private val stepText: TextView = ui.text(15f, R.color.ink, bold = true, center = true)
  private val stepCount: TextView = ui.text(13f, R.color.muted)
  private val stepPercent: TextView = ui.text(13f, R.color.good, bold = true)
  private val primary: Button = ui.primary { onPrimary() }
  private val battery: TextView = ui.text(15f, R.color.ink).apply {
    setText(R.string.home_battery_banner)
    background = ui.rounded(ui.color(R.color.warn_soft), 20)
    setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14))
    isClickable = true
    setOnClickListener { onKeepRunning() }
  }
  private var bob: ObjectAnimator? = null
  private var shownMascot: Mascot? = null

  val view: ScrollView

  init {
    val column = LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(24))
    }
    column.addView(ui.header(R.string.app_name))
    column.addView(update.view, ui.fullWidth(12))
    hero.addView(mascot, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, ui.dp(220)))
    hero.addView(title, ui.fullWidth(16))
    hero.addView(body, ui.fullWidth(8))
    hero.addView(progress, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(12)).apply {
      topMargin = ui.dp(20)
    })
    stepBox.addView(stepText, ui.fullWidth())
    stepBox.addView(LinearLayout(context).apply {
      orientation = LinearLayout.HORIZONTAL
      addView(stepCount, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
      addView(stepPercent)
    }, ui.fullWidth(4))
    hero.addView(stepBox, ui.fullWidth(10))
    hero.addView(primary, ui.fullWidth(20))
    column.addView(hero, ui.fullWidth(12))
    column.addView(battery, ui.fullWidth(12))
    view = ScrollView(context).apply { addView(column) }
  }

  /** [mayStop]: the phone may stop Nouride with the screen off (no battery exemption yet). */
  fun render(look: Look, mayStop: Boolean) {
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
      stepCount.text = context.getString(R.string.step_count, setup.step.ordinal + 1, Step.values().size)
      stepPercent.text = context.getString(R.string.percent, setup.percent)
    }
    val label = look.action.label()
    primary.visibility = if (label == null) View.GONE else View.VISIBLE
    if (label != null) primary.setText(label)
    // Not over the first setup, which asks for the exemption itself when Start is pressed.
    battery.visibility = if (mayStop && setup == null) View.VISIBLE else View.GONE
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
}
