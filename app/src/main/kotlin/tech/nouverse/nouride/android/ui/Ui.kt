package tech.nouverse.nouride.android.ui

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.view.Gravity
import android.view.Window
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import tech.nouverse.nouride.android.R
import tech.nouverse.nouride.android.server.Step

/** The pieces every screen is built from: soft cards, round buttons, calm text. No third-party UI library. */
internal class Ui(private val context: Context) {
  /** Whether the phone is set to dark: the colours follow by themselves (`values-night`), the system bars do not. */
  fun night(): Boolean =
    context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

  /** Dark status and navigation bar icons on the light background; at night the dark theme's light icons are right. */
  @Suppress("DEPRECATION")
  fun lightSystemBars(window: Window) {
    if (night()) return
    if (Build.VERSION.SDK_INT >= 26) {
      window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    } else if (Build.VERSION.SDK_INT >= 23) {
      window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
    }
  }

  fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

  fun color(id: Int): Int = context.getColor(id)

  fun rounded(fill: Int, radiusDp: Int, stroke: Int? = null): GradientDrawable = GradientDrawable().apply {
    setColor(fill)
    cornerRadius = dp(radiusDp).toFloat()
    if (stroke != null) setStroke(dp(1), stroke)
  }

  fun card(fill: Int = color(R.color.surface), padding: Int = 20): LinearLayout = LinearLayout(context).apply {
    orientation = LinearLayout.VERTICAL
    background = rounded(fill, 28, color(R.color.line))
    setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
  }

  fun text(sizeSp: Float, colorId: Int, bold: Boolean = false, center: Boolean = false): TextView = TextView(context).apply {
    textSize = sizeSp
    setTextColor(color(colorId))
    if (bold) setTypeface(typeface, Typeface.BOLD)
    if (center) gravity = Gravity.CENTER_HORIZONTAL
    setLineSpacing(0f, 1.15f)
  }

  /** The one big button of a screen: a pill in the peach pastel, ink words on it. */
  fun primary(onClick: () -> Unit): Button = Button(context).apply {
    isAllCaps = false
    textSize = 18f
    setTypeface(typeface, Typeface.BOLD)
    setTextColor(color(R.color.on_brand))
    background = rounded(color(R.color.brand), 32)
    stateListAnimator = null
    minHeight = dp(56)
    setOnClickListener { onClick() }
  }

  /** A quiet button: a soft peach fill, the peach deepened as words. */
  fun secondary(onClick: () -> Unit): Button = Button(context).apply {
    isAllCaps = false
    textSize = 16f
    setTextColor(color(R.color.brand_text))
    background = rounded(color(R.color.brand_soft), 24)
    stateListAnimator = null
    minHeight = dp(48)
    setOnClickListener { onClick() }
  }

  /** Words only, for what is rarely wanted: turning off. */
  fun quiet(onClick: () -> Unit): Button = Button(context).apply {
    isAllCaps = false
    textSize = 15f
    setTextColor(color(R.color.muted))
    background = null
    stateListAnimator = null
    setOnClickListener { onClick() }
  }

  /** A thick, round progress bar in sage mint, what is moving, on a soft track. */
  fun progressBar(): ProgressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
    val fill = ClipDrawable(rounded(color(R.color.progress), 8), Gravity.START, ClipDrawable.HORIZONTAL)
    progressDrawable = LayerDrawable(arrayOf(rounded(color(R.color.surface), 8, color(R.color.line)), fill)).apply {
      setId(0, android.R.id.background)
      setId(1, android.R.id.progress)
    }
    indeterminateTintList = ColorStateList.valueOf(color(R.color.progress))
    max = 100
    minimumHeight = dp(12)
  }

  /** The top of every tab, alike on each: the cat on its soft disc and the tab's name, so a tab never jumps in size. */
  fun header(titleId: Int): View = LinearLayout(context).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    addView(ImageView(context).apply {
      setImageResource(R.drawable.icon_cat)
      background = rounded(color(R.color.icon_background), 18)
      clipToOutline = true
    }, LinearLayout.LayoutParams(dp(36), dp(36)))
    addView(text(18f, R.color.ink, bold = true).apply {
      setText(titleId)
      setPadding(dp(10), 0, 0, 0)
    })
  }

  fun gap(heightDp: Int): View = View(context).apply { minimumHeight = dp(heightDp) }

  fun fullWidth(topMarginDp: Int = 0) = LinearLayout.LayoutParams(
    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
  ).apply { topMargin = dp(topMarginDp) }
}

internal fun Mascot.drawable(): Int = when (this) {
  Mascot.HELLO -> R.drawable.mascot_hello
  Mascot.RIDE -> R.drawable.mascot_ride
  Mascot.THUMBS -> R.drawable.mascot_thumbs
  Mascot.UNPLUGGED -> R.drawable.mascot_unplugged
}

internal fun Tone.fill(): Int = when (this) {
  Tone.CALM -> R.color.surface
  // What is moving is a plain card, as the dashboard's are; its mint is the progress bar.
  Tone.BUSY -> R.color.surface
  Tone.GOOD -> R.color.good_soft
  Tone.WARN -> R.color.warn_soft
}

internal fun Words.title(): Int = when (this) {
  Words.OFF -> R.string.off_title
  Words.SETTING_UP -> R.string.setting_up_title
  Words.STARTING -> R.string.starting_title
  Words.WAKING -> R.string.waking_title
  Words.RUNNING -> R.string.running_title
  Words.RESTARTING -> R.string.restarting_title
  Words.STOPPING -> R.string.stopping_title
  Words.FAILED -> R.string.failed_title
}

internal fun Words.body(): Int = when (this) {
  Words.OFF -> R.string.off_body
  Words.SETTING_UP -> R.string.setting_up_body
  Words.STARTING -> R.string.starting_body
  Words.WAKING -> R.string.waking_body
  Words.RUNNING -> R.string.running_body
  Words.RESTARTING -> R.string.restarting_body
  Words.STOPPING -> R.string.stopping_body
  Words.FAILED -> R.string.failed_body
}

internal fun Step.label(): Int = when (this) {
  Step.UNPACK -> R.string.step_unpack
  Step.PACKAGES -> R.string.step_packages
  Step.TOOLS -> R.string.step_tools
  Step.NOURIDE -> R.string.step_nouride
  Step.START -> R.string.step_start
}

internal fun Action.label(): Int? = when (this) {
  Action.START -> R.string.action_start
  Action.OPEN -> R.string.action_open
  Action.RETRY -> R.string.action_retry
  Action.NONE -> null
}
