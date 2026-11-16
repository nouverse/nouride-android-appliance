package tech.nouverse.nouride.android.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import tech.nouverse.nouride.android.R
import tech.nouverse.nouride.android.server.HealthPoller
import tech.nouverse.nouride.android.server.Place
import tech.nouverse.nouride.android.server.RemoteHost
import tech.nouverse.nouride.android.server.RemoteReply
import tech.nouverse.nouride.android.server.ServerState
import kotlin.concurrent.thread

/**
 * The one field the app ever asks a person to type in: the address of a Nouride on another computer, on a screen of its
 * own, as Versions and Technical Logs are, with their back at the top. Opened from the first screen ("Use Nouride on
 * another computer") and from More (Another computer, Change address), filled with the last address given.
 *
 * What is typed is read by `RemoteHost`, and each refusal says what to do in its own sentence under the field, where
 * the eye already is (`AddressForm`). An address that reads well is then asked once: no answer at all keeps the screen
 * open with what was typed. Connect hands the address back to `StatusActivity` ([EXTRA_BASE]), which keeps it and opens
 * Home; Cancel and back return where the person came from, with nothing changed.
 */
class AddressActivity : Activity() {
  private lateinit var ui: Ui
  private lateinit var field: EditText
  private lateinit var problem: TextView
  private lateinit var connect: Button

  /** In the appearance chosen on the More tab (`Appearances`). */
  override fun attachBaseContext(base: Context) = super.attachBaseContext(Appearances.wrap(base))

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    ui = Ui(this)
    ui.lightSystemBars(window)
    val place = Place(this)
    setContentView(views(place.lastAddress(), AddressForm.stopsHere(place.where(), ServerState.phase)))
    field.requestFocus()
  }

  private fun views(prefill: String?, stopsHere: Boolean): View {
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
        setText(R.string.where_ask_title)
        setPadding(ui.dp(6), 0, 0, 0)
      })
    })

    val card = ui.card(padding = 24)
    card.addView(ui.text(15f, R.color.muted).apply { setText(R.string.where_ask_body) })
    field = EditText(this).apply {
      setSingleLine(true)
      // A web address: the keyboard with `/` and `.`, and no word suggestions rewriting a name.
      inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
      imeOptions = EditorInfo.IME_ACTION_GO
      // An address is an identifier: DM Mono, as the rest of the app sets one.
      typeface = Fonts.typeface(context, Type.MONO)
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
      setOnEditorActionListener { _, action, _ ->
        if (action == EditorInfo.IME_ACTION_GO) submit()
        action == EditorInfo.IME_ACTION_GO
      }
    }
    card.addView(field, ui.fullWidth(16))
    problem = ui.text(14f, R.color.warn).apply { visibility = View.GONE }
    card.addView(problem, ui.fullWidth(8))
    if (stopsHere) {
      card.addView(ui.text(14f, R.color.ink).apply {
        setText(R.string.where_stops_here)
        background = ui.rounded(ui.color(R.color.warn_soft), 16)
        setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12))
      }, ui.fullWidth(14))
    }
    connect = ui.primary { submit() }.apply { setText(R.string.where_connect) }
    card.addView(connect, ui.fullWidth(20))
    card.addView(ui.quiet { finish() }.apply { setText(R.string.cancel) }, ui.fullWidth(4))

    val column = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(ui.dp(20), ui.dp(4), ui.dp(20), ui.dp(24))
      addView(card, ui.fullWidth())
    }
    root.addView(ScrollView(this).apply { addView(column) }, LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f,
    ))
    return root
  }

  private fun say(text: Int?) {
    problem.visibility = if (text == null) View.GONE else View.VISIBLE
    if (text != null) problem.setText(text)
  }

  private fun submit() {
    if (!connect.isEnabled) return
    val read = RemoteHost.read(field.text.toString())
    AddressForm.refusal(read)?.let { return say(it) }
    val base = (read as RemoteHost.Read.Address).base
    say(null)
    connect.isEnabled = false
    connect.setText(R.string.where_checking)
    thread(name = "remote-check") {
      val health = HealthPoller(RemoteReply.healthUrl(base), 0) {}.check()
      runOnUiThread {
        if (isFinishing || isDestroyed) return@runOnUiThread
        connect.isEnabled = true
        connect.setText(R.string.where_connect)
        if (!AddressForm.answered(health)) return@runOnUiThread say(R.string.where_unreachable)
        setResult(RESULT_OK, Intent().putExtra(EXTRA_BASE, base))
        finish()
      }
    }
  }

  companion object {
    /** The address taken, as `RemoteHost` made it. */
    const val EXTRA_BASE = "base"
  }
}
