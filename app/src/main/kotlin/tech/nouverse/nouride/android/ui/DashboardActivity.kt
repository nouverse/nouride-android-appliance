package tech.nouverse.nouride.android.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import tech.nouverse.nouride.android.Nouride
import tech.nouverse.nouride.android.server.FirstAdmin
import tech.nouverse.nouride.android.server.ServerService
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * The daemon's own dashboard, from the loopback address, already signed in: the app signs in with the account it made
 * on the first start (`FirstAdmin`) and hands the session cookie to the WebView. When that fails (the person changed
 * the password, or the install is older than the account) the dashboard shows its own login page, as anywhere else.
 */
class DashboardActivity : Activity() {
  private lateinit var web: WebView

  @SuppressLint("SetJavaScriptEnabled")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    web = WebView(this)
    web.settings.javaScriptEnabled = true
    web.settings.domStorageEnabled = true
    web.webViewClient = WebViewClient()
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
    if (web.canGoBack()) web.goBack() else @Suppress("DEPRECATION") super.onBackPressed()
  }

  override fun onDestroy() {
    web.destroy()
    super.onDestroy()
  }
}
