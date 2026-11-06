package tech.nouverse.nouride.android.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import tech.nouverse.nouride.android.Layout
import tech.nouverse.nouride.android.Nouride
import tech.nouverse.nouride.android.power.PowerSettings
import tech.nouverse.nouride.android.server.ServerService
import tech.nouverse.nouride.android.server.ServerState
import java.io.File
import java.io.RandomAccessFile
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.DateFormat
import java.util.Date

/**
 * The server's state, its addresses, Start and Stop, and the end of its log. The boolean extras
 * `start`, `stop` and `dashboard` do what the buttons do, for `adb shell am start --ez <name> true`.
 */
class StatusActivity : Activity() {
  private lateinit var layout: Layout
  private lateinit var phase: TextView
  private lateinit var health: TextView
  private lateinit var addresses: TextView
  private lateinit var battery: LinearLayout
  private lateinit var log: TextView
  private val handler = Handler(Looper.getMainLooper())
  private val listener: () -> Unit = { render() }
  private val refresh = object : Runnable {
    override fun run() {
      render()
      handler.postDelayed(this, 2_000)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    layout = Layout(this)
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
    if (intent.getBooleanExtra(EXTRA_DASHBOARD, false)) startActivity(Intent(this, DashboardActivity::class.java))
  }

  override fun onResume() {
    super.onResume()
    ServerState.listen(listener)
    handler.post(refresh)
  }

  override fun onPause() {
    ServerState.unlisten(listener)
    handler.removeCallbacks(refresh)
    super.onPause()
  }

  private fun startServer() {
    ServerService.start(this)
    if (!PowerSettings.isExempt(this)) startActivity(PowerSettings.exemptionRequest(this))
  }

  private fun views(): View {
    val pad = dp(16)
    val column = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(pad, pad, pad, pad)
    }
    column.addView(TextView(this).apply {
      text = "Nouride"
      textSize = 24f
      setTypeface(typeface, Typeface.BOLD)
    })
    phase = TextView(this).apply { textSize = 18f }
    health = TextView(this)
    addresses = TextView(this).apply { setTextIsSelectable(true) }
    column.addView(phase)
    column.addView(health)
    column.addView(addresses)
    column.addView(row(
      button("Start") { startServer() },
      button("Stop") { ServerService.stop(this) },
      button("Dashboard") { startActivity(Intent(this, DashboardActivity::class.java)) },
    ))
    battery = row(
      button("Allow in background") { startActivity(PowerSettings.exemptionRequest(this)) },
      button("Battery settings") { startActivity(PowerSettings.appDetails(this)) },
    )
    column.addView(battery)
    log = TextView(this).apply {
      typeface = Typeface.MONOSPACE
      textSize = 10f
      setTextIsSelectable(true)
    }
    column.addView(log)
    return ScrollView(this).apply { addView(column) }
  }

  private fun render() {
    phase.text = "${ServerState.phase.name.lowercase()}  ${ServerState.detail}"
    health.text = ServerState.health?.let {
      "/health: ${it.summary()} (${DateFormat.getTimeInstance().format(Date(ServerState.healthAt))})"
    } ?: "/health: not asked yet"
    val lan = lanAddress()
    addresses.text = "On this phone: ${Nouride.DASHBOARD_URL}\n" +
      (if (lan != null) "On your network: http://$lan:${Nouride.PORT}/dashboard/" else "Not on Wi-Fi")
    battery.visibility = if (PowerSettings.isExempt(this)) View.GONE else View.VISIBLE
    val file = if (layout.installed) layout.daemonLog else layout.installLog
    log.text = "${file.name}:\n${tail(file, 40)}"
  }

  private fun row(vararg views: View) = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    views.forEach(::addView)
  }

  private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
    text = label
    setOnClickListener { onClick() }
  }

  private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

  companion object {
    const val EXTRA_START = "start"
    const val EXTRA_STOP = "stop"
    const val EXTRA_DASHBOARD = "dashboard"

    private fun lanAddress(): String? =
      NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        .filter { it.isUp && !it.isLoopback && it.name.startsWith("wlan") }
        .flatMap { it.inetAddresses.toList() }
        .firstOrNull { it is Inet4Address }?.hostAddress

    private fun tail(file: File, lines: Int): String {
      if (!file.isFile) return "(no log yet)"
      RandomAccessFile(file, "r").use { raf ->
        val start = maxOf(0L, raf.length() - 16_384)
        raf.seek(start)
        val bytes = ByteArray((raf.length() - start).toInt())
        raf.readFully(bytes)
        return String(bytes).lines().takeLast(lines).joinToString("\n")
      }
    }
  }
}
