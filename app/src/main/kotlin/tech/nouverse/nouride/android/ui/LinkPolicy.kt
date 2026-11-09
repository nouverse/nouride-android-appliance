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

  /** The back button in the dashboard's header, drawn only inside this app (`commons/lib/home-app.ts` in Nouride). */
  const val HOME = "nouridehome://home"

  /** Where a link the dashboard follows goes. */
  enum class Link { BACK, HERE, OUTSIDE }

  /**
   * The header's back is the system's Back ([back]): a page back inside the dashboard, and from the first page it asks
   * before it closes (the owner, 2026-11-09: one tap there closed Nouride from any page). Nouride's own pages open here,
   * and a frame inside a page is the page's business; anything else leaves for the browser or the app that owns it.
   */
  fun link(url: String, isForMainFrame: Boolean): Link = when {
    url == HOME -> Link.BACK
    !isForMainFrame || inApp(url) -> Link.HERE
    else -> Link.OUTSIDE
  }

  /** Added to the WebView's user agent, so the dashboard knows it is inside the app and shows that way back. */
  const val USER_AGENT_MARK = "NourideHome/1"

  /**
   * Whether a failed load is Nouride itself not answering (turned off, or not up yet), shown as the cat and Try again:
   * the page the WebView was opening, on the phone's own Nouride. A picture or a script that failed is the page's.
   */
  fun unreachable(isForMainFrame: Boolean, url: String): Boolean = isForMainFrame && url != "about:blank" && inApp(url)

  /**
   * Whether the system's Back (the button, or the swipe from the edge) goes back a page inside the dashboard, as a
   * browser does (the owner, 2026-11-09), rather than leaving it for the app's own screen. Only onto an ordinary page
   * of Nouride's: never onto one a person passes once on the way in, the sign-in, choosing their first password or
   * the setup wizard, which is where walking the history like a browser once took a person; never out of Nouride, and
   * never past the first page. [history] is the WebView's, oldest first, and [current] the page on screen.
   */
  fun backInside(history: List<String>, current: Int): Boolean {
    val previous = history.getOrNull(current - 1) ?: return false
    return previous != "about:blank" && inApp(previous) && !passedOnce(previous)
  }

  /** What the system's Back does on the dashboard's screen. */
  enum class Back { PAGE, ASK, LEAVE }

  /**
   * Back a page when there is one ([backInside]). Otherwise Back would close the dashboard, which a swipe from the edge
   * does by accident, so it asks first (the owner, 2026-11-09). Over the cat that says Nouride is not answering, or
   * while the dashboard is still opening, there is nothing to lose: it leaves without asking.
   */
  fun back(history: List<String>, current: Int, dashboardShown: Boolean): Back = when {
    !dashboardShown -> Back.LEAVE
    backInside(history, current) -> Back.PAGE
    else -> Back.ASK
  }

  /** A dashboard page a person goes through once (`redirectFor` in the dashboard's `session.ts` sends them there). */
  fun passedOnce(url: String): Boolean {
    val path = try {
      URI(url).path ?: ""
    } catch (e: Exception) {
      return true
    }
    val page = path.removePrefix("/dashboard").trimEnd('/')
    return ONCE.any { page == it || page.startsWith("$it/") }
  }

  private val ONCE = listOf("/login", "/welcome", "/setup", "/oauth")

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
