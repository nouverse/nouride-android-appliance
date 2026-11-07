package tech.nouverse.nouride.android.ui

import tech.nouverse.nouride.android.Nouride
import java.net.URI

/**
 * What the dashboard's WebView may open itself: Nouride's own pages on the phone (the dashboard, the LLM gateway's
 * page under it), nothing else. Every other link leaves for the browser or the app that owns it: a sign-in page there
 * has the person's own session, and a stranger's page never runs with the dashboard's cookie.
 */
object LinkPolicy {
  private val local = URI(Nouride.ORIGIN)

  fun inApp(url: String): Boolean {
    if (url == "about:blank") return true
    val uri = try {
      URI(url)
    } catch (e: Exception) {
      return false
    }
    val host = uri.host ?: return false
    return uri.scheme == "http" && (host == local.host || host == "localhost") && uri.port == local.port
  }
}
