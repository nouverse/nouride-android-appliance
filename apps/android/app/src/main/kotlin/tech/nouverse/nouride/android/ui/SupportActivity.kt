package tech.nouverse.nouride.android.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import tech.nouverse.nouride.android.Layout
import tech.nouverse.nouride.android.Nouride
import tech.nouverse.nouride.android.R
import tech.nouverse.nouride.android.server.NetworkAccess
import tech.nouverse.nouride.android.server.ServerState
import tech.nouverse.nouride.android.update.Updates

/**
 * "Technical Logs": everything technical, on a screen of its own, for whoever helps the person. The main screen only
 * has a quiet link to it. The log scrolls on its own here, the whole screen given to it rather than a box inside a
 * scrolling page, and opens at its end. Copy and Share send the whole report, Share straight into a chat.
 */
class SupportActivity : Activity() {
  private lateinit var ui: Ui
  private lateinit var layout: Layout
  private lateinit var summary: TextView
  private lateinit var log: TextView
  private lateinit var scroll: ScrollView
  private var report = ""

  /** In the appearance chosen on the More tab (`Appearances`). */
  override fun attachBaseContext(base: Context) = super.attachBaseContext(Appearances.wrap(base))

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    ui = Ui(this)
    layout = Layout(this)
    ui.lightSystemBars(window)
    setContentView(views())
  }

  override fun onResume() {
    super.onResume()
    load()
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
        setText(R.string.support_title)
        setPadding(ui.dp(6), 0, 0, 0)
      })
    })

    summary = mono(R.color.ink)
    root.addView(ui.card(padding = 14).apply { addView(summary) }, LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { setMargins(ui.dp(16), 0, ui.dp(16), 0) })

    // Long log lines scroll sideways rather than wrapping into a wall of half-lines.
    log = mono(R.color.muted).apply { setHorizontallyScrolling(true) }
    scroll = ScrollView(this).apply {
      isFillViewport = true
      addView(HorizontalScrollView(context).apply { addView(log) })
    }
    root.addView(ui.card(padding = 14).apply { addView(scroll) }, LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f,
    ).apply { setMargins(ui.dp(16), ui.dp(12), ui.dp(16), 0) })

    root.addView(LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(16))
      addView(ui.secondary { copy() }.apply { setText(R.string.copy) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
      addView(View(context), LinearLayout.LayoutParams(ui.dp(12), 1))
      addView(ui.primary { share() }.apply { setText(R.string.share) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    })
    return root
  }

  private fun mono(colorId: Int): TextView = ui.text(12f, colorId).apply {
    typeface = Fonts.typeface(context, Type.MONO)
    setTextIsSelectable(true)
  }

  private fun load() {
    val health = ServerState.health
    val lan = SupportText.lanAddress()
    val facts = SupportText.summary(listOf(
      "state" to "${ServerState.phase.name.lowercase()} ${ServerState.detail}".trim(),
      "health" to (health?.summary() ?: "not asked yet"),
      "local" to Nouride.DASHBOARD_URL,
      "lan" to when {
        layout.installEnv.isFile && !NetworkAccess.open(layout.installEnv.readText()) -> "off (this phone only)"
        lan == null -> "no Wi-Fi"
        else -> "http://$lan:${Nouride.PORT}/dashboard/"
      },
      "app" to Updates.installedVersion(this),
      "android" to "${Build.VERSION.RELEASE} (${Build.MANUFACTURER} ${Build.MODEL})",
    ))
    val nouride = if (layout.installed) layout.daemonLog else layout.installLog
    val logs = listOf(
      layout.serviceLog.name to SupportText.readTail(layout.serviceLog, 40),
      nouride.name to SupportText.readTail(nouride, 300),
    )
    report = SupportText.report(facts, logs)
    summary.text = facts
    log.text = SupportText.report("", logs).trimStart()
    scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
  }

  private fun copy() {
    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(getString(R.string.support_title), report))
    Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
  }

  private fun share() {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
      .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.support_title))
      .putExtra(Intent.EXTRA_TEXT, report)
    startActivity(Intent.createChooser(send, null))
  }
}
