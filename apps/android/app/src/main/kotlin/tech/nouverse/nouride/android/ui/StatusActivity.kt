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
import tech.nouverse.nouride.android.server.HealthPoller
import tech.nouverse.nouride.android.server.Place
import tech.nouverse.nouride.android.server.RemoteReply
import tech.nouverse.nouride.android.server.Reply
import tech.nouverse.nouride.android.server.ServerService
import tech.nouverse.nouride.android.server.ServerState
import tech.nouverse.nouride.android.server.Where
import tech.nouverse.nouride.android.update.NourideNotice
import tech.nouverse.nouride.android.update.Updates
import kotlin.concurrent.thread

/**
 * The app's screen, three tabs along a bar at the bottom: Home (the cat, how Nouride is, one big button), Connect
 * (Nouride on another device) and More (keeping it running, this app's version, turning it off, Technical Logs). The
 * users are not technical, so the server's phases, addresses and log stay behind Technical Logs (`SupportActivity`,
 * `StatusLook`, and the home-ui skill). The bar is hidden while the first start sets Nouride up.
 *
 * A phone without Nouride first asks where it runs (`Place`): on this phone, started when that is pressed, or on another
 * computer at an address the person gives. Nothing of the phone's own server starts until it is answered, and answered
 * "another computer" this screen never starts one at all. More changes the answer either way.
 *
 * The extras `start`, `stop` and `dashboard` (booleans) do what the buttons do, and `tab` (home, connect, more) opens a
 * tab, for `adb shell am start --ez <name> true` and `--es tab connect`.
 */
class StatusActivity : Activity() {
  private lateinit var ui: Ui
  private lateinit var update: UpdateCard
  private lateinit var nourideUpdate: NourideUpdateCard
  private lateinit var home: HomeTab
  private lateinit var connect: ConnectTab
  private lateinit var more: MoreTab
  private lateinit var bar: BottomBar
  private lateinit var place: Place
  private var tab = Tab.HOME
  private val handler = Handler(Looper.getMainLooper())
  private val listener: () -> Unit = {
    render()
    update.serverChanged()
    askNouride()
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
    place = Place(this)
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
    render()
    checkRemote()
    nourideAsked = false
    askNouride()
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

  /** Never while Nouride lives elsewhere: there is no server of this phone's to start. */
  private fun startServer() {
    if (place.where() == Where.REMOTE) return openDashboard()
    update.starting()
    ServerService.start(this)
    if (!PowerSettings.isExempt(this)) startActivity(PowerSettings.exemptionRequest(this))
  }

  private fun openDashboard() = startActivity(Intent(this, DashboardActivity::class.java))

  private fun views(): View {
    // More's Versions line says what the check found, not what was known before it.
    update = UpdateCard(this, ui, onChecked = { more.resume() })
    nourideUpdate = NourideUpdateCard(this, ui) {
      startActivity(Intent(this, VersionsActivity::class.java).putExtra(VersionsActivity.EXTRA_UPDATE_NOURIDE, true))
    }
    home = HomeTab(
      this, ui, update, nourideUpdate,
      onPrimary = ::onPrimary,
      onKeepRunning = { show(Tab.MORE) },
      onRunHere = ::runOnThisPhone,
      onConnectRemote = ::askForAddress,
    )
    connect = ConnectTab(this, ui, onGoHome = { show(Tab.HOME) })
    more = MoreTab(this, ui, onTurnOff = ::confirmTurnOff, onChoose = ::choose, onChangeAddress = ::askForAddress)
    bar = BottomBar(this, ui, onSelect = ::show)
    val content = FrameLayout(this).apply {
      addView(home.view)
      addView(connect.view)
      addView(more.view)
    }
    return LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      background = ui.page()
      addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
      addView(bar.view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        setMargins(ui.dp(12), ui.dp(4), ui.dp(12), ui.dp(12))
      })
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
    val where = place.where()
    val address = place.address()
    val look = if (where == Where.REMOTE) StatusLook.remote(reply)
    else StatusLook.of(ServerState.phase, answering = ServerState.health?.ok == true)
    val mayStop = where == Where.PHONE && !PowerSettings.isExempt(this)
    val barShown = Tabs.barShown(look, firstStart = ServerState.progress != null)
    if (!barShown && tab != Tab.HOME) return show(Tab.HOME)
    bar.view.visibility = if (barShown) View.VISIBLE else View.GONE
    // Nouride's own update, once it answers and the first start is over: the card's Update needs it running.
    val nourideReady = NourideNotice.waiting(this)
      ?.takeIf { where == Where.PHONE && ServerState.health?.ok == true && ServerState.progress == null }
    nourideUpdate.render(nourideReady)
    bar.attention(Tab.MORE, UpdateNotices.moreDot(mayStop, app = Updates.waiting(this) != null, nouride = nourideReady != null))
    home.render(look, mayStop, where, address)
    if (tab == Tab.CONNECT) connect.render(look, where, address)
    more.render(mayStop, where != Where.REMOTE && StatusLook.canTurnOff(ServerState.phase), where, address)
  }

  private fun onPrimary() {
    if (place.where() == Where.REMOTE) {
      // Open while that computer may answer; Try again when it did not.
      return if (StatusLook.remote(reply).action == Action.RETRY) checkRemote(again = true) else openDashboard()
    }
    when (StatusLook.of(ServerState.phase, ServerState.health?.ok == true).action) {
      Action.START, Action.RETRY -> startServer()
      Action.OPEN -> openDashboard()
      Action.NONE -> Unit
    }
  }

  /**
   * "Run it on this phone", on the first screen or on More: the app as it always was, started at once, as the choice
   * is the person asking for it. The install already here is picked up as it was.
   */
  private fun runOnThisPhone() {
    place.onThisPhone()
    reply = null
    show(Tab.HOME)
    if (ServerState.phase == ServerState.Phase.STOPPED || ServerState.phase == ServerState.Phase.FAILED) startServer()
  }

  /** More's choice: this phone at once; another computer only once its address is given and answers. */
  private fun choose(where: Where) {
    if (where == place.where()) return
    if (where == Where.PHONE) runOnThisPhone() else askForAddress()
  }

  /** The address of a Nouride on another computer, on its own screen (`AddressActivity`); back here, [onActivityResult]. */
  private fun askForAddress() {
    startActivityForResult(Intent(this, AddressActivity::class.java), REQUEST_ADDRESS)
  }

  /**
   * An address given and answering: Nouride lives there now, and Home says how it is. Anything this phone was running
   * is turned off, so a phone that has moved on does not keep two; the address screen said so before. Cancel or back
   * leaves the screen as it was, on the first screen or on More.
   */
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    val base = data?.getStringExtra(AddressActivity.EXTRA_BASE)
    if (requestCode != REQUEST_ADDRESS || resultCode != RESULT_OK || base == null) return
    place.onMachine(base)
    if (ServerState.phase != ServerState.Phase.STOPPED) ServerService.stop(this)
    show(Tab.HOME)
    checkRemote(again = true)
  }

  /** Asked once each time the screen comes back, as soon as Nouride answers (`NourideNotice.check` keeps it to 15 minutes). */
  private var nourideAsked = false

  private fun askNouride() {
    if (nourideAsked || place.where() != Where.PHONE || ServerState.health?.ok != true) return
    nourideAsked = true
    thread(name = "nouride-update-notice") {
      NourideNotice.check(this, Updates.ON_OPEN_MS)
      handler.post {
        render()
        more.resume()
      }
    }
  }

  /** What the other computer's Nouride says (`RemoteReply`), asked when the screen comes back and on Try again. */
  private var reply: Reply? = null
  private var replyFor: String? = null
  private var asking = 0

  /**
   * [again]: the person asked (Try again, a new address), so the card says it is checking. Coming back to the screen
   * keeps the last answer up until the new one is in, rather than flicker through "checking" each time.
   */
  private fun checkRemote(again: Boolean = false) {
    val base = place.address() ?: return
    val ask = ++asking
    if (again || replyFor != base) {
      reply = null
      render()
    }
    thread(name = "remote-health") {
      val answer = RemoteReply.of(HealthPoller(RemoteReply.healthUrl(base), 0) {}.check())
      handler.post {
        // A later question, or another address, owns the screen now.
        if (ask != asking || place.address() != base) return@post
        reply = answer
        replyFor = base
        render()
      }
    }
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
    private const val REQUEST_ADDRESS = 1
  }
}
