package tech.nouverse.nouride.android.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
 * Back always goes to the app's own screen, never through the dashboard's history: walking that like a browser took a
 * person from the dashboard back to the setup wizard. The dashboard's header has the same way back inside the app
 * (`LinkPolicy.HOME`), so there is one bar on screen, the dashboard's. The user agent says it is the app.
 */
class DashboardActivity : Activity() {
  private lateinit var web: WebView

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
    setContentView(web)
    if (savedInstanceState != null) {
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
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
      val url = request.url.toString()
      if (url == LinkPolicy.HOME) {
        goHome()
        return true
      }
      // A frame inside a page is the page's business; only where the page itself goes is decided here.
      if (!request.isForMainFrame || LinkPolicy.inApp(url)) return false
      openOutside(url)
      return true
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
      if (connection.responseCode != 200) null
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
  override fun onBackPressed() {
    goHome()
  }

  override fun onDestroy() {
    web.destroy()
    super.onDestroy()
  }
}
