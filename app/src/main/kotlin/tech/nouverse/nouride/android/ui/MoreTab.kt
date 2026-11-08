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
import tech.nouverse.nouride.android.update.Updates
import java.io.IOException
import kotlin.concurrent.thread

/**
 * The More tab, for what is rarely needed: keeping Nouride running (the battery exemption, with a check once it is
 * given, and on Xiaomi's phones the Autostart switch), this app's version and a check for a newer one, turning Nouride
 * off, and Technical Logs for whoever helps. Turning off lives here so it is never pressed by mistake on Home.
 */
internal class MoreTab(
  private val activity: Activity,
  private val ui: Ui,
  private val onTurnOff: () -> Unit,
  private val onUpdateFound: () -> Unit,
) {
  private val batteryOk = LinearLayout(activity).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
  }
  private val batteryAsk = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
  private val version: TextView = ui.text(15f, R.color.muted)
  private val updateLine: TextView = ui.text(15f, R.color.ink)
  private val check: Button = ui.secondary { checkNow() }.apply { setText(R.string.check_updates) }
  private val turnOff: Button = ui.quiet { onTurnOff() }.apply {
    setText(R.string.turn_off_nouride)
    setTextColor(ui.color(R.color.warn))
  }
  private var checking = false

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
      running.addView(ui.text(15f, R.color.muted).apply { setText(R.string.autostart_body) }, ui.fullWidth(4))
      running.addView(ui.secondary { activity.startActivity(PowerSettings.appDetails(activity)) }.apply {
        setText(R.string.open_settings)
      }, ui.fullWidth(12))
    }
    column.addView(running, ui.fullWidth(16))

    column.addView(ui.card().apply {
      addView(ui.text(17f, R.color.ink, bold = true).apply { setText(R.string.appearance_title) })
      addView(appearance(), ui.fullWidth(12))
    }, ui.fullWidth(12))

    column.addView(ui.card().apply {
      addView(ui.text(17f, R.color.ink, bold = true).apply { setText(R.string.app_name) })
      addView(version, ui.fullWidth(2))
      addView(updateLine, ui.fullWidth(10))
      addView(check, ui.fullWidth(12))
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
      row.addView(ui.text(15f, if (on) R.color.on_brand else R.color.ink, bold = on, center = true).apply {
        setText(label)
        gravity = Gravity.CENTER
        maxLines = 1
        // "Same as phone" is the longest: smaller rather than cut off, on a narrow phone.
        if (Build.VERSION.SDK_INT >= 26) setAutoSizeTextTypeUniformWithConfiguration(11, 15, 1, TypedValue.COMPLEX_UNIT_SP)
        else textSize = 13f
        setPadding(ui.dp(4), ui.dp(11), ui.dp(4), ui.dp(11))
        if (on) background = ui.rounded(ui.color(R.color.brand), 20)
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

  private fun divider(): View = View(activity).apply { setBackgroundColor(ui.color(R.color.line)) }

  /** [mayStop]: no battery exemption yet. [canTurnOff]: Nouride is on, or on its way. */
  fun render(mayStop: Boolean, canTurnOff: Boolean) {
    batteryOk.visibility = if (mayStop) View.GONE else View.VISIBLE
    batteryAsk.visibility = if (mayStop) View.VISIBLE else View.GONE
    turnOff.visibility = if (canTurnOff) View.VISIBLE else View.GONE
  }

  /** On the screen's resume: the version, and what the last check found, without asking the feed. */
  fun resume() {
    version.text = activity.getString(R.string.app_version, Updates.installedVersion(activity))
    if (!checking) showFound(Updates.waiting(activity)?.version)
  }

  private fun showFound(newer: String?) {
    updateLine.text = if (newer == null) activity.getString(R.string.up_to_date)
    else activity.getString(R.string.update_ready_more, newer)
  }

  private fun checkNow() {
    if (checking) return
    checking = true
    check.isEnabled = false
    updateLine.setText(R.string.checking)
    thread(name = "update-check-now", isDaemon = true) {
      val result = try {
        Result.success(Updates.checkNow(activity))
      } catch (e: IOException) {
        Result.failure(e)
      }
      activity.runOnUiThread {
        checking = false
        check.isEnabled = true
        if (activity.isDestroyed) return@runOnUiThread
        result.onSuccess { found ->
          showFound(found?.version)
          if (found != null) onUpdateFound()
        }.onFailure { updateLine.setText(R.string.check_failed) }
      }
    }
  }
}
