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

  /** The dashboard's "Back to Nouride Home" (`commons/lib/home-app.ts` in Nouride): closes the dashboard. */
  const val HOME = "nouridehome://home"

  /** Added to the WebView's user agent, so the dashboard knows it is inside the app and shows that way back. */
  const val USER_AGENT_MARK = "NourideHome/1"

  /**
   * Whether a failed load is Nouride itself not answering (turned off, or not up yet), shown as the cat and Try again:
   * the page the WebView was opening, on the phone's own Nouride. A picture or a script that failed is the page's.
   */
  fun unreachable(isForMainFrame: Boolean, url: String): Boolean = isForMainFrame && url != "about:blank" && inApp(url)

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
