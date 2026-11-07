package tech.nouverse.nouride.android.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import tech.nouverse.nouride.android.Nouride

/** The daemon's own dashboard, from the loopback address. */
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
    if (savedInstanceState == null) web.loadUrl(Nouride.DASHBOARD_URL) else web.restoreState(savedInstanceState)
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
