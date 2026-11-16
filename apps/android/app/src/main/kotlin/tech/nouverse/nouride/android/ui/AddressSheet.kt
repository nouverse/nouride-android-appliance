package tech.nouverse.nouride.android.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import tech.nouverse.nouride.android.R
import tech.nouverse.nouride.android.server.Health
import tech.nouverse.nouride.android.server.HealthPoller
import tech.nouverse.nouride.android.server.RemoteHost
import tech.nouverse.nouride.android.server.RemoteReply
import kotlin.concurrent.thread

/**
 * The one field the app ever asks a person to type in: the address of a Nouride on another computer. A card over the
 * screen in the app's own look, not Android's plain dialog: a title, one sentence, the field, then Connect and Cancel.
 *
 * What is typed is read by `RemoteHost`, and each refusal says what to do in its own sentence under the field, where
 * the eye already is. An address that reads well is then asked once: no answer at all (a name that does not exist, a
 * computer that cannot be reached) keeps the card open with what was typed, as a typo is the likeliest cause. Any
 * answer is taken, even a page in front of Nouride or a tunnel whose computer is off: Home says how that computer is.
 *
 * [stopsHere]: Nouride runs on this phone now, and connecting turns it off, which the card says before it happens.
 */
internal fun addressSheet(
  activity: Activity,
  ui: Ui,
  prefill: String?,
  stopsHere: Boolean,
  onConnected: (base: String) -> Unit,
): Dialog {
  val dialog = Dialog(activity)
  val main = Handler(Looper.getMainLooper())
  val content = ui.card(padding = 24, floating = false)
  content.addView(ui.text(20f, R.color.ink, bold = true).apply { setText(R.string.where_ask_title) })
  content.addView(ui.text(15f, R.color.muted).apply { setText(R.string.where_ask_body) }, ui.fullWidth(6))
  val field = EditText(activity).apply {
    setSingleLine(true)
    // A web address: the keyboard with `/` and `.`, and no word suggestions rewriting a name.
    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    imeOptions = EditorInfo.IME_ACTION_GO
    // An address is an identifier: DM Mono, as the rest of the app sets one.
    typeface = Fonts.typeface(activity, Type.MONO)
    textSize = 16f
    setTextColor(ui.color(R.color.ink))
    setHintTextColor(ui.color(R.color.muted))
    setHint(R.string.where_address_hint)
    background = ui.rounded(ui.color(R.color.background), 16, ui.color(R.color.line))
    setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14))
    if (prefill != null) {
      setText(RemoteHost.shown(prefill))
      setSelection(text.length)
    }
  }
  content.addView(field, ui.fullWidth(16))
  val problem: TextView = ui.text(14f, R.color.warn).apply { visibility = View.GONE }
  content.addView(problem, ui.fullWidth(8))
  if (stopsHere) {
    content.addView(ui.text(14f, R.color.ink).apply {
      setText(R.string.where_stops_here)
      background = ui.rounded(ui.color(R.color.warn_soft), 16)
      setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12))
    }, ui.fullWidth(14))
  }
  lateinit var connect: Button

  fun say(text: Int?) {
    problem.visibility = if (text == null) View.GONE else View.VISIBLE
    if (text != null) problem.setText(text)
  }

  fun submit() {
    if (!connect.isEnabled) return
    val base = when (val read = RemoteHost.read(field.text.toString())) {
      is RemoteHost.Read.Address -> read.base
      RemoteHost.Read.Empty -> return say(R.string.where_empty)
      RemoteHost.Read.PlainHttp -> return say(R.string.where_plain_http)
      RemoteHost.Read.NotAnAddress -> return say(R.string.where_not_an_address)
    }
    say(null)
    connect.isEnabled = false
    connect.setText(R.string.where_checking)
    thread(name = "remote-check") {
      val health: Health = HealthPoller(RemoteReply.healthUrl(base), 0) {}.check()
      main.post {
        if (!dialog.isShowing) return@post
        connect.isEnabled = true
        connect.setText(R.string.where_connect)
        // No HTTP answer at all: nothing is listening at that name, most likely a typo.
        if (health.httpCode == 0) return@post say(R.string.where_unreachable)
        dialog.dismiss()
        onConnected(base)
      }
    }
  }

  connect = ui.primary { submit() }.apply { setText(R.string.where_connect) }
  content.addView(connect, ui.fullWidth(20))
  content.addView(ui.quiet { dialog.dismiss() }.apply { setText(R.string.cancel) }, ui.fullWidth(4))
  field.setOnEditorActionListener { _, action, _ ->
    if (action == EditorInfo.IME_ACTION_GO) submit()
    action == EditorInfo.IME_ACTION_GO
  }
  dialog.setContentView(content)
  dialog.window?.apply {
    setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    setLayout(minOf(activity.resources.displayMetrics.widthPixels - ui.dp(32), ui.dp(420)), ViewGroup.LayoutParams.WRAP_CONTENT)
    setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
  }
  field.requestFocus()
  dialog.show()
  return dialog
}
