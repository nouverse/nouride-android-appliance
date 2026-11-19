package tech.nouverse.nouride.android.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import tech.nouverse.nouride.android.Nouride
import tech.nouverse.nouride.android.R
import tech.nouverse.nouride.android.server.Place
import tech.nouverse.nouride.android.signin.CallbackCatcher
import tech.nouverse.nouride.android.signin.DeviceSignIn
import tech.nouverse.nouride.android.signin.LocalDevice
import tech.nouverse.nouride.android.signin.LoopbackSignIn
import kotlin.concurrent.thread

/**
 * The daemon's own dashboard, from the loopback address, always signed in: the app trades its device token for a
 * session and hands the cookie to the WebView when the screen opens, and again whenever the dashboard reaches its
 * sign-in page, whatever ended the session (`signin/DeviceSignIn`). While Nouride lives on another machine
 * (`server/Place`), the same screen shows that machine's dashboard instead, over https, with its own login page as the
 * way in: a session there is the person's, not this app's to make. When the device's sign-in gives up (a Nouride before
 * 0.8.40, which has none) the dashboard shows its own login page, as anywhere else.
 *
 * A file the dashboard downloads is kept in the phone's Downloads (`DashboardDownloads`).
 *
 * Only Nouride's own pages open here (`LinkPolicy`); every other link leaves for the browser or the app that owns it,
 * a provider's sign-in page included (`LoopbackSignIn`). `nouridehome://dashboard`, where a finished sign-in sends the
 * browser, brings this screen back where it was, and the dashboard asks the gateway again for its accounts.
 *
 * Back, the phone's button or swipe and the back button in the dashboard's header alike (`LinkPolicy.HOME`), goes back
 * a page inside the dashboard as a browser does (`LinkPolicy.back`); from the first page it asks before it closes the
 * dashboard. Never back onto the sign-in, the first password or the setup wizard: walking the history onto those once
 * took a person from the dashboard back to the wizard. There is one bar on screen, the dashboard's; the user agent says
 * it is the app, which is what draws that back button.
 */
class DashboardActivity : Activity() {
  private lateinit var web: WebView
  private lateinit var opening: View
  private lateinit var unreachable: View
  private var failed: String? = null
  private lateinit var downloads: DashboardDownloads

  /** The Nouride this screen shows: this phone's own, or the machine a person pointed the app at (`server/Place`). */
  private lateinit var aim: DashboardAim
  private val origin get() = aim.origin
  private val start get() = aim.start

  /** This phone's own Nouride: the only one the app signs in to. */
  private val local get() = aim.local

  /** The screen was pointed at another Nouride: what came before is the other one's, and Back must not reach it. */
  private var forgetHistory = false

  /** In the appearance chosen on the More tab (`Appearances`). */
  override fun attachBaseContext(base: Context) = super.attachBaseContext(Appearances.wrap(base))

  @SuppressLint("SetJavaScriptEnabled")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Nouride on another machine: that machine's session is its own, so the app signs in with nothing and the
    // dashboard's own login page takes over, as it does on any other device.
    aim = DashboardAim.of(Place(this).address())
    web = WebView(this)
    web.settings.javaScriptEnabled = true
    web.settings.domStorageEnabled = true
    web.settings.setSupportMultipleWindows(true)
    web.settings.javaScriptCanOpenWindowsAutomatically = true
    web.webViewClient = Guard()
    web.webChromeClient = NewWindows()
    web.settings.userAgentString = "${web.settings.userAgentString} ${LinkPolicy.USER_AGENT_MARK}"
    downloads = DashboardDownloads(this, web, { origin }, ::openOutside).also { it.attach() }
    // The sign-in and the first page take a moment: a blank white WebView looked broken. The app's own background and
    // the cat until the dashboard is on screen.
    val ui = Ui(this)
    web.setBackgroundColor(ui.color(R.color.background))
    opening = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER
      setBackgroundColor(ui.color(R.color.background))
      addView(ImageView(context).apply {
        setImageResource(R.drawable.mascot_ride)
        adjustViewBounds = true
      }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, ui.dp(160)))
      addView(ui.text(17f, R.color.ink, bold = true, center = true).apply { setText(R.string.dashboard_opening) }, ui.fullWidth(16))
      addView(ui.progressBar().apply { isIndeterminate = true }, LinearLayout.LayoutParams(ui.dp(160), ui.dp(12)).apply { topMargin = ui.dp(16) })
    }
    // Nouride turned off, or not up yet: the cat and Try again, never the WebView's own error page with its
    // net::ERR_CONNECTION_REFUSED, which is all a person saw before.
    unreachable = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER
      setBackgroundColor(ui.color(R.color.background))
      setPadding(ui.dp(32), 0, ui.dp(32), 0)
      visibility = View.GONE
      isClickable = true
      addView(ImageView(context).apply {
        setImageResource(R.drawable.mascot_unplugged)
        adjustViewBounds = true
      }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, ui.dp(160)))
      addView(ui.text(20f, R.color.ink, bold = true, center = true).apply { setText(R.string.dashboard_unreachable_title) }, ui.fullWidth(16))
      addView(ui.text(15f, R.color.muted, center = true).apply { setText(R.string.dashboard_unreachable_body) }, ui.fullWidth(6))
      addView(ui.primary { retry() }.apply { setText(R.string.action_retry) }, ui.fullWidth(20))
      addView(ui.quiet { goHome() }.apply { setText(R.string.signin_back) }, ui.fullWidth(4))
    }
    setContentView(FrameLayout(this).apply {
      addView(web)
      addView(opening)
      addView(unreachable)
    })
    ui.lightSystemBars(window)
    if (savedInstanceState != null) {
      opening.visibility = View.GONE
      web.restoreState(savedInstanceState)
      return
    }
    load()
  }

  /** A machine of the person's own: nothing to sign in with here, and its login page is the way in. */
  private fun load() {
    if (!local) web.loadUrl(start) else signIn(onOpen = true) { web.loadUrl(start) }
  }

  /**
   * One round with the app's device token, off the main thread, the session handed to the WebView; then [then] on the
   * main thread, with whether it signed in. A round on opening that Nouride answered and that gave up counts for
   * [DeviceSignIn.again], so the sign-in page it leaves is not tried again at once; one that nothing answered (Nouride
   * still starting) does not, so the round after Try again can sign in.
   */
  private fun signIn(onOpen: Boolean, then: (signedIn: Boolean) -> Unit) {
    val device = LocalDevice(applicationContext)
    thread(name = "dashboard-sign-in") {
      val started = System.currentTimeMillis()
      val outcome = device.signIn()
      runOnUiThread {
        if (isDestroyed) return@runOnUiThread
        when (outcome) {
          is DeviceSignIn.Outcome.SignedIn -> {
            CookieManager.getInstance().setCookie(Nouride.ORIGIN, outcome.cookie)
            CookieManager.getInstance().flush()
          }
          is DeviceSignIn.Outcome.GaveUp -> if (onOpen && DeviceSignIn.gaveUpCounts(outcome.code)) lastRound = started
        }
        then(outcome is DeviceSignIn.Outcome.SignedIn)
      }
    }
  }

  /**
   * The dashboard reached its sign-in page on this phone's Nouride: its session ended (its week ran out, it was ended
   * elsewhere or revoked, Sign out, the WebView's data cleared). Unless a round from it ran seconds ago, sign in again
   * behind the cat and go back where the person was; a round that gives up leaves the page on screen.
   */
  private fun signInAgain(url: String) {
    val now = System.currentTimeMillis()
    if (!DeviceSignIn.again(lastRound, now)) return
    lastRound = now
    opening.visibility = View.VISIBLE
    signIn(onOpen = false) { signedIn ->
      if (signedIn) web.loadUrl(DeviceSignIn.afterSignIn(url, Nouride.ORIGIN)) else opening.visibility = View.GONE
    }
  }

  private fun retry() {
    unreachable.visibility = View.GONE
    opening.visibility = View.VISIBLE
    web.loadUrl(failed ?: start)
  }

  /** The status screen, whether it is under this one or not: after a sign-in this screen may be the only one left. */
  private fun goHome() {
    startActivity(Intent(this, StatusActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    finish()
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    DashboardAim.onOpen(aim, Place(this).address())?.let { return aimAt(it) }
    // Not a reload: the setup wizard keeps where the person is in memory, and a reload put them back on its first
    // step. The dashboard asks again for what it shows when the page becomes visible (react-query's refetch on
    // focus), and this says so in case the WebView did not.
    if (intent.data?.toString() == CallbackCatcher.BACK) {
      web.evaluateJavascript("document.dispatchEvent(new Event('visibilitychange'))", null)
    }
  }

  /** Where Nouride lives changed since this screen was made: the other one's dashboard, from its first page. */
  private fun aimAt(next: DashboardAim) {
    aim = next
    failed = null
    web.stopLoading()
    forgetHistory = true
    unreachable.visibility = View.GONE
    opening.visibility = View.VISIBLE
    load()
  }

  /** Nouride's pages stay; anything else goes out, redirects too: `/oauth/start` answers with Google's page. */
  private inner class Guard : WebViewClient() {
    /** Every move of the page's address, its router's included: how the dashboard reaches its sign-in page. */
    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
      if (local && DeviceSignIn.signInPage(url, Nouride.ORIGIN)) signInAgain(url)
    }

    override fun onPageCommitVisible(view: WebView, url: String) {
      if (LinkPolicy.inApp(url, origin) && url != "about:blank") {
        opening.visibility = View.GONE
        downloads.inject()
      }
    }

    /** Again once the page is whole: a page restored from its saved state may show no commit. */
    override fun onPageFinished(view: WebView, url: String) {
      if (!LinkPolicy.inApp(url, origin)) return
      downloads.inject()
      if (forgetHistory && url != "about:blank") {
        view.clearHistory()
        forgetHistory = false
      }
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
      if (!LinkPolicy.unreachable(request.isForMainFrame, request.url.toString(), origin)) return
      failed = request.url.toString()
      opening.visibility = View.GONE
      unreachable.visibility = View.VISIBLE
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
      val url = request.url.toString()
      return when (LinkPolicy.link(url, request.isForMainFrame, origin)) {
        LinkPolicy.Link.BACK -> {
          back()
          true
        }
        LinkPolicy.Link.HERE -> false
        LinkPolicy.Link.OUTSIDE -> {
          openOutside(url)
          true
        }
      }
    }
  }

  /**
   * A link that opens a new tab (the gateway's "Open sign-in") goes to a WebView of its own that is never shown: a
   * page of Nouride's moves into this one, anything else, or what a Nouride path redirects to, goes out.
   */
  private inner class NewWindows : WebChromeClient() {
    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
      val popup = WebView(this@DashboardActivity)
      popup.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
          val url = request.url.toString()
          if (LinkPolicy.inApp(url, origin)) return false
          openOutside(url)
          v.destroy()
          return true
        }

        override fun onPageFinished(v: WebView, url: String) {
          if (url == "about:blank" || !LinkPolicy.inApp(url, origin)) return
          web.loadUrl(url)
          v.destroy()
        }
      }
      (resultMsg.obj as WebView.WebViewTransport).webView = popup
      resultMsg.sendToTarget()
      return true
    }
  }

  private fun openOutside(url: String) {
    LoopbackSignIn.expect(url, System.currentTimeMillis())?.let { CallbackCatcher.arm(this, it) }
    val intent = if (url.startsWith("intent:")) {
      try {
        Intent.parseUri(url, Intent.URI_INTENT_SCHEME).apply { component = null; selector = null }
      } catch (e: Exception) {
        return
      }
    } else {
      Intent(Intent.ACTION_VIEW, Uri.parse(url))
    }
    try {
      startActivity(intent.addCategory(Intent.CATEGORY_BROWSABLE))
    } catch (e: ActivityNotFoundException) {
      Toast.makeText(this, R.string.no_app_for_link, Toast.LENGTH_SHORT).show()
    }
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == DashboardDownloads.PERMISSION_REQUEST) {
      downloads.permissionAnswered(grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    web.saveState(outState)
  }

  @Deprecated("Activity.onBackPressed")
  override fun onBackPressed() = back()

  /** The phone's Back, or the header's: a page back, or leaving, asked first when there is something to lose. */
  private fun back() {
    val list = web.copyBackForwardList()
    val history = (0 until list.size).map { list.getItemAtIndex(it).url }
    val shown = unreachable.visibility != View.VISIBLE && opening.visibility != View.VISIBLE
    when (LinkPolicy.back(history, list.currentIndex, dashboardShown = shown, origin = origin)) {
      LinkPolicy.Back.PAGE -> web.goBack()
      LinkPolicy.Back.LEAVE -> goHome()
      LinkPolicy.Back.ASK -> askToLeave()
    }
  }

  /** Before the dashboard closes: a swipe from the edge, or a tap on the header's back, closed it by accident. */
  private fun askToLeave() {
    AlertDialog.Builder(this)
      .setTitle(R.string.leave_dashboard_title)
      .setMessage(R.string.leave_dashboard_body)
      .setPositiveButton(R.string.leave_dashboard) { _, _ -> goHome() }
      .setNegativeButton(R.string.stay, null)
      .show()
  }

  override fun onDestroy() {
    web.destroy()
    super.onDestroy()
  }

  companion object {
    /** When the last round from the sign-in page, or the last on opening that gave up, started (`DeviceSignIn.again`). */
    @Volatile
    private var lastRound: Long? = null
  }
}
