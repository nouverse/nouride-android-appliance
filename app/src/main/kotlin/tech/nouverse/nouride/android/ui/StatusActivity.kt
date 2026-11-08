package tech.nouverse.nouride.android.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import tech.nouverse.nouride.android.R
import tech.nouverse.nouride.android.power.PowerSettings
import tech.nouverse.nouride.android.server.ServerService
import tech.nouverse.nouride.android.server.ServerState

/**
 * The app's screen, three tabs along a bar at the bottom: Home (the cat, how Nouride is, one big button), Connect
 * (Nouride on another device) and More (keeping it running, this app's version, turning it off, Technical Logs). The
 * users are not technical, so the server's phases, addresses and log stay behind Technical Logs (`SupportActivity`,
 * `StatusLook`, and the android-appliance-ui skill). The bar is hidden while the first start sets Nouride up.
 * The extras `start`, `stop` and `dashboard` (booleans) do what the buttons do, and `tab` (home, connect, more) opens a
 * tab, for `adb shell am start --ez <name> true` and `--es tab connect`.
 */
class StatusActivity : Activity() {
  private lateinit var ui: Ui
  private lateinit var update: UpdateCard
  private lateinit var home: HomeTab
  private lateinit var connect: ConnectTab
  private lateinit var more: MoreTab
  private lateinit var bar: BottomBar
  private lateinit var barLine: View
  private var tab = Tab.HOME
  private val handler = Handler(Looper.getMainLooper())
  private val listener: () -> Unit = {
    render()
    update.serverChanged()
  }
  private val refresh = object : Runnable {
    override fun run() {
      render()
      handler.postDelayed(this, 2_000)
    }
  }

  /** In the appearance chosen on the More tab (`Appearances`). */
  override fun attachBaseContext(base: Context) = super.attachBaseContext(Appearances.wrap(base))

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    ui = Ui(this)
    ui.lightSystemBars(window)
    setContentView(views())
    show(savedInstanceState?.getString(STATE_TAB)?.let { name -> Tab.values().firstOrNull { it.name == name } } ?: Tab.HOME)
    if (savedInstanceState == null) handleExtras(intent)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleExtras(intent)
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putString(STATE_TAB, tab.name)
  }

  private fun handleExtras(intent: Intent) {
    if (intent.getBooleanExtra(EXTRA_START, false)) startServer()
    if (intent.getBooleanExtra(EXTRA_STOP, false)) ServerService.stop(this)
    if (intent.getBooleanExtra(EXTRA_DASHBOARD, false)) openDashboard()
    intent.getStringExtra(EXTRA_TAB)?.let { name -> Tab.values().firstOrNull { it.name.equals(name, ignoreCase = true) }?.let(::show) }
  }

  override fun onResume() {
    super.onResume()
    ServerState.listen(listener)
    handler.post(refresh)
    update.resume()
    more.resume()
  }

  override fun onPause() {
    ServerState.unlisten(listener)
    handler.removeCallbacks(refresh)
    super.onPause()
  }

  @Deprecated("Activity.onBackPressed")
  override fun onBackPressed() {
    val to = Tabs.back(tab)
    @Suppress("DEPRECATION")
    if (to == null) super.onBackPressed() else show(to)
  }

  private fun startServer() {
    update.starting()
    ServerService.start(this)
    if (!PowerSettings.isExempt(this)) startActivity(PowerSettings.exemptionRequest(this))
  }

  private fun openDashboard() = startActivity(Intent(this, DashboardActivity::class.java))

  private fun views(): View {
    // More said "You have the newest version" behind the dialog offering one: its line was read before the check.
    update = UpdateCard(this, ui, onChecked = { more.resume() })
    home = HomeTab(this, ui, update, onPrimary = ::onPrimary, onKeepRunning = { show(Tab.MORE) })
    connect = ConnectTab(this, ui, onGoHome = { show(Tab.HOME) })
    more = MoreTab(this, ui, onTurnOff = ::confirmTurnOff, onUpdateFound = {
      show(Tab.HOME)
      update.resume()
    })
    bar = BottomBar(this, ui, onSelect = ::show)
    val content = FrameLayout(this).apply {
      addView(home.view)
      addView(connect.view)
      addView(more.view)
    }
    barLine = View(this).apply { setBackgroundColor(ui.color(R.color.line)) }
    return LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(ui.color(R.color.background))
      addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
      addView(barLine, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(1)))
      addView(bar.view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }
  }

  private fun show(next: Tab) {
    tab = next
    home.view.visibility = if (next == Tab.HOME) View.VISIBLE else View.GONE
    connect.view.visibility = if (next == Tab.CONNECT) View.VISIBLE else View.GONE
    more.view.visibility = if (next == Tab.MORE) View.VISIBLE else View.GONE
    bar.select(next)
    render()
  }

  private fun render() {
    val look = StatusLook.of(ServerState.phase, answering = ServerState.health?.ok == true)
    val mayStop = !PowerSettings.isExempt(this)
    val barShown = Tabs.barShown(look, firstStart = ServerState.progress != null)
    if (!barShown && tab != Tab.HOME) return show(Tab.HOME)
    bar.view.visibility = if (barShown) View.VISIBLE else View.GONE
    barLine.visibility = bar.view.visibility
    bar.attention(Tab.MORE, mayStop)
    home.render(look, mayStop)
    if (tab == Tab.CONNECT) connect.render(look)
    more.render(mayStop, StatusLook.canTurnOff(ServerState.phase))
  }

  private fun onPrimary() = when (StatusLook.of(ServerState.phase, ServerState.health?.ok == true).action) {
    Action.START, Action.RETRY -> startServer()
    Action.OPEN -> openDashboard()
    Action.NONE -> Unit
  }

  private fun confirmTurnOff() {
    AlertDialog.Builder(this)
      .setTitle(R.string.turn_off_title)
      .setMessage(R.string.turn_off_body)
      .setPositiveButton(R.string.turn_off) { _, _ -> ServerService.stop(this) }
      .setNegativeButton(R.string.cancel, null)
      .show()
  }

  companion object {
    const val EXTRA_START = "start"
    const val EXTRA_STOP = "stop"
    const val EXTRA_DASHBOARD = "dashboard"
    const val EXTRA_TAB = "tab"
    private const val STATE_TAB = "tab"
  }
}
