package tech.nouverse.nouride.android.ui

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
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

/**
 * The pieces every screen is built from, in Nouride's soft look (the dashboard's): floating cards, pill buttons, Fredoka
 * and Manrope. No third-party UI library.
 */
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

  /** A card floats, as the dashboard's do: 22dp corners, a hairline and a soft shadow; one in a dialog does not. */
  fun card(fill: Int = color(R.color.surface), padding: Int = 20, floating: Boolean = true): LinearLayout = LinearLayout(context).apply {
    orientation = LinearLayout.VERTICAL
    background = rounded(fill, CARD_RADIUS_DP, color(R.color.line))
    setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
    if (floating) float(this, 4)
  }

  /**
   * A soft, wide shadow in the theme's shade, or in [shadow] for the main button's glow of its own colour. Android 9 and
   * later colour a shadow; before that one would be plain grey, so there is none.
   */
  fun float(view: View, elevationDp: Int, shadow: Int = color(R.color.shade)) {
    if (Build.VERSION.SDK_INT < 28) return
    view.elevation = dp(elevationDp).toFloat()
    view.outlineAmbientShadowColor = shadow
    view.outlineSpotShadowColor = shadow
  }

  /**
   * The card that says how Nouride is, drawn as the dashboard's greeting: lit at its left in the mood's colour ([light]),
   * white in the middle, peach at its right.
   */
  fun hero(light: Int): GradientDrawable =
    GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(light, color(R.color.surface), color(R.color.brand_soft))).apply {
      cornerRadius = dp(CARD_RADIUS_DP).toFloat()
      setStroke(dp(1), color(R.color.line))
    }

  /** The page behind everything: cream (violet grey at night) with a faint lavender light at its top right. */
  fun page(): LayerDrawable = LayerDrawable(arrayOf(
    GradientDrawable().apply { setColor(color(R.color.background)) },
    GradientDrawable().apply {
      gradientType = GradientDrawable.RADIAL_GRADIENT
      gradientRadius = dp(420).toFloat()
      setGradientCenter(1f, 0f)
      colors = intArrayOf(color(R.color.page_light), Color.TRANSPARENT)
    },
  ))

  fun text(sizeSp: Float, colorId: Int, bold: Boolean = false, center: Boolean = false): TextView = TextView(context).apply {
    textSize = sizeSp
    setTextColor(color(colorId))
    val type = Fonts.of(sizeSp, bold)
    typeface = Fonts.typeface(context, type)
    // Fredoka a little tight, as the dashboard sets it.
    if (type == Type.HEADING) letterSpacing = -0.01f
    if (center) gravity = Gravity.CENTER_HORIZONTAL
    setLineSpacing(0f, 1.15f)
  }

  /** The one big button of a screen: a pill in the peach pastel, ink words on it. */
  fun primary(onClick: () -> Unit): Button = Button(context).apply {
    isAllCaps = false
    textSize = 18f
    typeface = Fonts.typeface(context, Type.STRONG)
    setTextColor(color(R.color.on_brand))
    background = rounded(color(R.color.brand), 32)
    stateListAnimator = null
    // The main action may glow faintly in its own colour; nothing else does.
    float(this, 6, color(R.color.brand))
    minHeight = dp(56)
    setOnClickListener { onClick() }
  }

  /** A quiet button: a soft peach fill, the peach deepened as words. */
  fun secondary(onClick: () -> Unit): Button = Button(context).apply {
    isAllCaps = false
    textSize = 16f
    typeface = Fonts.typeface(context, Type.STRONG)
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
    typeface = Fonts.typeface(context, Type.BODY)
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

  companion object {
    /** The design rules' card corner. */
    const val CARD_RADIUS_DP = 22
  }

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

/** The light at the left of the card that says how Nouride is: lavender as the greeting has it, mint when it runs, pink when it needs you. */
internal fun Tone.light(): Int = when (this) {
  Tone.CALM, Tone.BUSY -> R.color.hero_light
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
