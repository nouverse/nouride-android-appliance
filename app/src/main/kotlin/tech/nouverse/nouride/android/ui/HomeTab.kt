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
import tech.nouverse.nouride.android.server.Where

/**
 * The Home tab, the one a person needs: the cat, one sentence of how Nouride is, and at most one big button. Above it
 * a new version of the app when there is one (`UpdateCard`); under it, only while the phone may stop Nouride, one line
 * that leads to More, where that is fixed.
 *
 * On the very first screen it asks the one thing the app cannot answer for the person: whether this phone should run
 * Nouride, or open a Nouride that already runs on another machine (`server/Place`). Answered "another machine", the tab
 * carries that machine's card instead of the state of a server this phone does not run.
 */
internal class HomeTab(
  private val context: Context,
  private val ui: Ui,
  update: UpdateCard,
  private val onPrimary: () -> Unit,
  private val onKeepRunning: () -> Unit,
  private val onRunHere: () -> Unit,
  private val onConnectRemote: () -> Unit,
  private val onChangePlace: () -> Unit,
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

  /** The first screen: one question, two answers, and nothing runs until one of them is pressed. */
  private val choose: LinearLayout = ui.card(padding = 24).apply {
    gravity = Gravity.CENTER_HORIZONTAL
    addView(ImageView(context).apply {
      setImageResource(R.drawable.mascot_hello)
      adjustViewBounds = true
    }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, ui.dp(180)))
    addView(ui.text(24f, R.color.ink, bold = true, center = true).apply { setText(R.string.where_title) }, ui.fullWidth(16))
    addView(ui.text(16f, R.color.muted, center = true).apply { setText(R.string.where_body) }, ui.fullWidth(8))
    addView(ui.primary { onRunHere() }.apply { setText(R.string.where_phone) }, ui.fullWidth(22))
    addView(ui.secondary { onConnectRemote() }.apply { setText(R.string.where_remote) }, ui.fullWidth(8))
  }

  private val remoteAddress: TextView = ui.text(16f, R.color.brand_text, bold = true, center = true).apply {
    // An address is an identifier: DM Mono, as the dashboard and the Connect tab set one.
    typeface = Fonts.typeface(context, Type.MONO)
    setTextIsSelectable(true)
    maxLines = 2
    background = ui.rounded(ui.color(R.color.brand_soft), 18)
    setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12))
  }

  /** Nouride on another machine: where it is, and the one button this phone is for. */
  private val remote: LinearLayout = ui.card(padding = 24).apply {
    gravity = Gravity.CENTER_HORIZONTAL
    addView(ImageView(context).apply {
      setImageResource(R.drawable.mascot_thumbs)
      adjustViewBounds = true
    }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, ui.dp(180)))
    addView(ui.text(24f, R.color.ink, bold = true, center = true).apply { setText(R.string.remote_title) }, ui.fullWidth(16))
    addView(ui.text(16f, R.color.muted, center = true).apply { setText(R.string.remote_body) }, ui.fullWidth(8))
    addView(remoteAddress, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
      topMargin = ui.dp(18)
      bottomMargin = ui.dp(2)
    })
    addView(ui.primary { onPrimary() }.apply { setText(R.string.action_open) }, ui.fullWidth(20))
    addView(ui.quiet { onChangePlace() }.apply { setText(R.string.remote_change) }, ui.fullWidth(4))
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
    column.addView(choose, ui.fullWidth(12))
    column.addView(hero, ui.fullWidth(12))
    column.addView(remote, ui.fullWidth(12))
    column.addView(battery, ui.fullWidth(12))
    view = ScrollView(context).apply { addView(column) }
  }

  /**
   * [mayStop]: the phone may stop Nouride with the screen off (no battery exemption yet). [where]: null until the person
   * has said where Nouride lives, which is the question this tab opens on.
   */
  fun render(look: Look, mayStop: Boolean, where: Where?, address: String?) {
    choose.visibility = if (where == null) View.VISIBLE else View.GONE
    remote.visibility = if (where == Where.REMOTE) View.VISIBLE else View.GONE
    hero.visibility = if (where == Where.PHONE) View.VISIBLE else View.GONE
    // A server this phone does not run has nothing to say about its battery.
    battery.visibility = if (where == Where.PHONE && mayStop && ServerState.progress == null) View.VISIBLE else View.GONE
    if (where != Where.PHONE) {
      bobbing(false)
      shownMascot = null
      remoteAddress.text = address.orEmpty()
      return
    }
    if (look.mascot != shownMascot) {
      mascot.setImageResource(look.mascot.drawable())
      shownMascot = look.mascot
    }
    bobbing(look.busy)
    hero.background = ui.hero(ui.color(look.tone.light()))
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
