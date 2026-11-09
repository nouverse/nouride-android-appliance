package tech.nouverse.nouride.android.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
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
import org.json.JSONObject
import tech.nouverse.nouride.android.Nouride
import tech.nouverse.nouride.android.R
import tech.nouverse.nouride.android.server.FirstAdmin
import tech.nouverse.nouride.android.server.ServerService
import tech.nouverse.nouride.android.signin.CallbackCatcher
import tech.nouverse.nouride.android.signin.LoopbackSignIn
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * The daemon's own dashboard, from the loopback address, already signed in: the app signs in with the account it made
 * on the first start (`FirstAdmin`) and hands the session cookie to the WebView. When that fails (the person changed
 * the password, or the install is older than the account) the dashboard shows its own login page, as anywhere else.
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

  /** In the appearance chosen on the More tab (`Appearances`). */
  override fun attachBaseContext(base: Context) = super.attachBaseContext(Appearances.wrap(base))

  @SuppressLint("SetJavaScriptEnabled")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    web = WebView(this)
    web.settings.javaScriptEnabled = true
    web.settings.domStorageEnabled = true
    web.settings.setSupportMultipleWindows(true)
    web.settings.javaScriptCanOpenWindowsAutomatically = true
    web.webViewClient = Guard()
    web.webChromeClient = NewWindows()
    web.settings.userAgentString = "${web.settings.userAgentString} ${LinkPolicy.USER_AGENT_MARK}"
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
    val password = getSharedPreferences(ServerService.PREFS, MODE_PRIVATE).getString(ServerService.PREF_ADMIN_PASSWORD, null)
    if (password == null) {
      web.loadUrl(Nouride.DASHBOARD_URL)
      return
    }
    thread(name = "dashboard-sign-in") {
      val cookie = signIn(password)
      runOnUiThread {
        if (isDestroyed) return@runOnUiThread
        if (cookie != null) {
          CookieManager.getInstance().setCookie(Nouride.ORIGIN, cookie)
          CookieManager.getInstance().flush()
        }
        web.loadUrl(Nouride.DASHBOARD_URL)
      }
    }
  }

  private fun retry() {
    unreachable.visibility = View.GONE
    opening.visibility = View.VISIBLE
    web.loadUrl(failed ?: Nouride.DASHBOARD_URL)
  }

  /** The status screen, whether it is under this one or not: after a sign-in this screen may be the only one left. */
  private fun goHome() {
    startActivity(Intent(this, StatusActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    finish()
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    // Not a reload: the setup wizard keeps where the person is in memory, and a reload put them back on its first
    // step. The dashboard asks again for what it shows when the page becomes visible (react-query's refetch on
    // focus), and this says so in case the WebView did not.
    if (intent.data?.toString() == CallbackCatcher.BACK) {
      web.evaluateJavascript("document.dispatchEvent(new Event('visibilitychange'))", null)
    }
  }

  /** Nouride's pages stay; anything else goes out, redirects too: `/oauth/start` answers with Google's page. */
  private inner class Guard : WebViewClient() {
    override fun onPageCommitVisible(view: WebView, url: String) {
      if (LinkPolicy.inApp(url) && url != "about:blank") opening.visibility = View.GONE
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
      if (!LinkPolicy.unreachable(request.isForMainFrame, request.url.toString())) return
      failed = request.url.toString()
      opening.visibility = View.GONE
      unreachable.visibility = View.VISIBLE
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
      val url = request.url.toString()
      return when (LinkPolicy.link(url, request.isForMainFrame)) {
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
          if (LinkPolicy.inApp(url)) return false
          openOutside(url)
          v.destroy()
          return true
        }

        override fun onPageFinished(v: WebView, url: String) {
          if (url == "about:blank" || !LinkPolicy.inApp(url)) return
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

  /** The session cookie for the app's account, or null when the sign-in did not work. Never logs the password. */
  private fun signIn(password: String): String? {
    val connection = try {
      URL(Nouride.LOGIN_URL).openConnection() as HttpURLConnection
    } catch (e: IOException) {
      return null
    }
    return try {
      connection.requestMethod = "POST"
      connection.connectTimeout = 5_000
      connection.readTimeout = 10_000
      connection.doOutput = true
      connection.setRequestProperty("content-type", "application/json")
      val body = JSONObject().put("username", FirstAdmin.USERNAME).put("password", password).toString()
      connection.outputStream.use { it.write(body.toByteArray()) }
      val code = connection.responseCode
      if (FirstAdmin.forgets(code)) {
        getSharedPreferences(ServerService.PREFS, MODE_PRIVATE).edit().remove(ServerService.PREF_ADMIN_PASSWORD).apply()
      }
      if (code != 200) null
      else FirstAdmin.sessionCookie(connection.headerFields.entries.filter { it.key.equals("set-cookie", true) }.flatMap { it.value })
    } catch (e: IOException) {
      null
    } finally {
      connection.disconnect()
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
    when (LinkPolicy.back(history, list.currentIndex, dashboardShown = shown)) {
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
}
