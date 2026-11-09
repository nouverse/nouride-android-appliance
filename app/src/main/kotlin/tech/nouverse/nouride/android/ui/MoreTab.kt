package tech.nouverse.nouride.android.ui

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import tech.nouverse.nouride.android.R
import tech.nouverse.nouride.android.power.PowerSettings
import tech.nouverse.nouride.android.server.ServerState
import tech.nouverse.nouride.android.update.Updates

/**
 * The More tab, for what is rarely needed: keeping Nouride running (the battery exemption, with a check once it is
 * given, and on Xiaomi's phones the Autostart switch), the appearance, Versions and updates (this app's and Nouride's,
 * on a screen of their own), turning Nouride off, and Technical Logs for whoever helps. Turning off lives here so it is
 * never pressed by mistake on Home.
 */
internal class MoreTab(
  private val activity: Activity,
  private val ui: Ui,
  private val onTurnOff: () -> Unit,
) {
  private val batteryOk = LinearLayout(activity).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
  }
  private val batteryAsk = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
  private val versions: TextView = ui.text(15f, R.color.muted)
  private val turnOff: Button = ui.quiet { onTurnOff() }.apply {
    setText(R.string.turn_off_nouride)
    setTextColor(ui.color(R.color.warn))
  }

  val view: ScrollView

  init {
    val column = LinearLayout(activity).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(24))
    }
    column.addView(ui.header(R.string.tab_more))

    val running = ui.card()
    running.addView(ui.text(17f, R.color.ink, bold = true).apply { setText(R.string.keep_running_title) })
    batteryOk.addView(ImageView(activity).apply {
      setImageResource(R.drawable.ic_check)
      imageTintList = ColorStateList.valueOf(ui.color(R.color.good))
    }, LinearLayout.LayoutParams(ui.dp(22), ui.dp(22)))
    batteryOk.addView(ui.text(15f, R.color.muted).apply {
      setText(R.string.battery_ok)
      setPadding(ui.dp(10), 0, 0, 0)
    }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    running.addView(batteryOk, ui.fullWidth(10))
    batteryAsk.addView(ui.text(15f, R.color.muted).apply { setText(R.string.battery_body) })
    batteryAsk.addView(ui.primary { activity.startActivity(PowerSettings.exemptionRequest(activity)) }.apply {
      setText(R.string.battery_allow)
    }, ui.fullWidth(12))
    running.addView(batteryAsk, ui.fullWidth(8))
    if (PowerSettings.asksForAutostart()) {
      running.addView(divider(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(1)).apply {
        topMargin = ui.dp(16)
      })
      running.addView(ui.text(15f, R.color.ink, bold = true).apply { setText(R.string.autostart_title) }, ui.fullWidth(14))
      // The app's own name, as the list shows it: Nouride Home, or Nouride Home Dev beside it.
      running.addView(ui.text(15f, R.color.muted).apply {
        text = activity.getString(R.string.autostart_body, activity.getString(R.string.app_name))
      }, ui.fullWidth(4))
      running.addView(ui.secondary { openAutostart() }.apply {
        setText(R.string.open_settings)
      }, ui.fullWidth(12))
    }
    column.addView(running, ui.fullWidth(16))

    column.addView(ui.card().apply {
      addView(ui.text(17f, R.color.ink, bold = true).apply { setText(R.string.appearance_title) })
      addView(appearance(), ui.fullWidth(12))
    }, ui.fullWidth(12))

    column.addView(ui.card(padding = 18).apply {
      isClickable = true
      setOnClickListener { activity.startActivity(Intent(activity, VersionsActivity::class.java)) }
      addView(ui.text(16f, R.color.ink).apply { setText(R.string.versions_row) })
      addView(versions, ui.fullWidth(2))
    }, ui.fullWidth(12))

    column.addView(turnOff, ui.fullWidth(16))
    column.addView(ui.card(padding = 18).apply {
      isClickable = true
      setOnClickListener { activity.startActivity(Intent(activity, SupportActivity::class.java)) }
      addView(ui.text(16f, R.color.ink).apply { setText(R.string.details) })
    }, ui.fullWidth(8))
    view = ScrollView(activity).apply { addView(column) }
  }

  /** Same as phone, Light or Dark, side by side; a choice applies at once, the screen drawn again in it. */
  private fun appearance(): View {
    val chosen = Appearances.get(activity)
    val row = LinearLayout(activity).apply {
      orientation = LinearLayout.HORIZONTAL
      background = ui.rounded(ui.color(R.color.background), 24, ui.color(R.color.line))
      setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4))
    }
    for ((each, label) in listOf(
      Appearance.PHONE to R.string.appearance_phone,
      Appearance.LIGHT to R.string.appearance_light,
      Appearance.DARK to R.string.appearance_dark,
    )) {
      val on = each == chosen
      row.addView(ui.text(15f, R.color.ink, bold = on, center = true).apply {
        setText(label)
        gravity = Gravity.CENTER
        maxLines = 1
        // "Same as phone" is the longest: smaller rather than cut off, on a narrow phone.
        if (Build.VERSION.SDK_INT >= 26) setAutoSizeTextTypeUniformWithConfiguration(11, 15, 1, TypedValue.COMPLEX_UNIT_SP)
        else textSize = 13f
        setPadding(ui.dp(4), ui.dp(11), ui.dp(4), ui.dp(11))
        if (on) background = ui.rounded(ui.color(R.color.place_soft), 20)
        isClickable = true
        isSelected = on
        setOnClickListener {
          if (each == Appearances.get(activity)) return@setOnClickListener
          Appearances.set(activity, each)
          activity.recreate()
        }
      }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }
    return row
  }

  /** Xiaomi's Autostart list; the app's page when the list refuses to open after all (a permission, a newer HyperOS). */
  private fun openAutostart() {
    try {
      activity.startActivity(PowerSettings.autostartSettings(activity))
    } catch (e: RuntimeException) {
      activity.startActivity(PowerSettings.appDetails(activity))
    }
  }

  private fun divider(): View = View(activity).apply { setBackgroundColor(ui.color(R.color.line)) }

  /** [mayStop]: no battery exemption yet. [canTurnOff]: Nouride is on, or on its way. */
  fun render(mayStop: Boolean, canTurnOff: Boolean) {
    batteryOk.visibility = if (mayStop) View.GONE else View.VISIBLE
    batteryAsk.visibility = if (mayStop) View.VISIBLE else View.GONE
    turnOff.visibility = if (canTurnOff) View.VISIBLE else View.GONE
  }

  /** On the screen's resume: the two versions, as far as they are known without asking anyone. */
  fun resume() {
    val app = Updates.installedVersion(activity)
    val nouride = ServerState.health?.version?.removePrefix("v")
    versions.text = when {
      Updates.waiting(activity) != null -> activity.getString(R.string.versions_row_update)
      nouride == null -> activity.getString(R.string.versions_row_app, app)
      else -> activity.getString(R.string.versions_row_both, app, nouride)
    }
  }
}
