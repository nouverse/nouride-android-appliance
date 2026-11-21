package tech.nouverse.nouride.android.ui

import tech.nouverse.nouride.android.Nouride
import java.net.URI

/**
 * What the dashboard's WebView may open itself: Nouride's own pages at whichever Nouride this screen is showing (the
 * dashboard, the LLM gateway's page under it), nothing else. Every other link leaves for the browser or the app that
 * owns it: a sign-in page there has the person's own session, and a stranger's page never runs with the dashboard's
 * cookie.
 *
 * The origin is a parameter rather than a constant since Nouride can be on another machine (`server/Place`): the
 * phone's own loopback, or the address a person was given. The rules are the same for both, and a page of the other
 * machine is as far outside as a stranger's.
 */
object LinkPolicy {
  private val local = URI(Nouride.ORIGIN)

  /**
   * "Back to Nouride Home", which the dashboard draws in its menu and on its pages without a header, only inside this
   * app (`commons/lib/home-app.ts` in Nouride).
   */
  const val HOME = "nouridehome://home"

  /** Back, the chevron the dashboard draws in a phone's header. Sent only to an app whose mark says 2 or more. */
  const val BACK = "nouridehome://back"

  /** Where a link the dashboard follows goes. [BACK] is the system's Back ([back]); [LEAVE] is [leave]. */
  enum class Link { BACK, LEAVE, HERE, OUTSIDE }

  /**
   * The header's Back is the system's Back ([back]): a page back inside the dashboard, and from the first page it asks
   * before it closes (the owner, 2026-11-09: one tap there closed Nouride from any page). "Back to Nouride Home" is
   * never that ([leave]): it leaves from any page, and asks first (the owner, 2026-11-20: the two are different
   * buttons). Nouride's own pages open here, and a frame inside a page is the page's business; anything else leaves
   * for the browser or the app that owns it.
   */
  fun link(url: String, isForMainFrame: Boolean, origin: String = Nouride.ORIGIN): Link = when {
    url == BACK -> Link.BACK
    url == HOME -> Link.LEAVE
    !isForMainFrame || inApp(url, origin) -> Link.HERE
    else -> Link.OUTSIDE
  }

  /**
   * Added to the WebView's user agent, so the dashboard knows it is inside the app and shows those ways back. The
   * number says which links the app catches: 2 is [HOME] and [BACK]. Raise it only with the code that catches a new
   * link.
   */
  const val USER_AGENT_MARK = "NourideHome/2"

  /**
   * Whether a failed load is Nouride itself not answering (turned off, not up yet, or the other machine unreachable),
   * shown as the cat and Try again: the page the WebView was opening, at that Nouride. A picture or a script that
   * failed is the page's.
   */
  fun unreachable(isForMainFrame: Boolean, url: String, origin: String = Nouride.ORIGIN): Boolean =
    isForMainFrame && url != "about:blank" && inApp(url, origin)

  /**
   * Whether the system's Back (the button, or the swipe from the edge) goes back a page inside the dashboard, as a
   * browser does (the owner, 2026-11-09), rather than leaving it for the app's own screen. Only onto an ordinary page
   * of Nouride's: never onto one a person passes once on the way in, the sign-in, choosing their first password or
   * the setup wizard, which is where walking the history like a browser once took a person; never out of Nouride, and
   * never past the first page. Never from one of those pages either: until that step is done the dashboard sends every
   * other page back to it, so Back from the first password went to `/dashboard/`, which put the person on it again, and
   * Back never left. [history] is the WebView's, oldest first, and [current] the page on screen.
   */
  fun backInside(history: List<String>, current: Int, origin: String = Nouride.ORIGIN): Boolean {
    val previous = history.getOrNull(current - 1) ?: return false
    val onScreen = history.getOrNull(current) ?: "about:blank"
    return !passedOnce(onScreen) && previous != "about:blank" && inApp(previous, origin) && !passedOnce(previous)
  }

  /** What the system's Back, or "Back to Nouride Home", does on the dashboard's screen. */
  enum class Back { PAGE, ASK, LEAVE }

  /**
   * Back a page when there is one ([backInside]). Otherwise Back would close the dashboard, which a swipe from the edge
   * does by accident, so it asks first (the owner, 2026-11-09). Over the cat that says Nouride is not answering, or
   * while the dashboard is still opening, there is nothing to lose: it leaves without asking.
   */
  fun back(history: List<String>, current: Int, dashboardShown: Boolean, origin: String = Nouride.ORIGIN): Back = when {
    !dashboardShown -> Back.LEAVE
    backInside(history, current, origin) -> Back.PAGE
    else -> Back.ASK
  }

  /**
   * "Back to Nouride Home": it asks first whatever the page, the first one included, since leaving loses the page a
   * person is on. Over the cat, or while the dashboard opens, it leaves without asking. Never a page back.
   */
  fun leave(dashboardShown: Boolean): Back = if (dashboardShown) Back.ASK else Back.LEAVE

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

  /**
   * Whether [url] is a page of the Nouride at [origin]: that machine's own name, port and scheme. The phone's own
   * server answers over http and a machine reached through a Cloudflare tunnel over https, and a page served the other
   * way on the same name and port is not that dashboard. `localhost` is the phone's loopback under another name, and
   * nothing else is.
   */
  fun inApp(url: String, origin: String = Nouride.ORIGIN): Boolean {
    if (url == "about:blank") return true
    val uri = parse(url) ?: return false
    val here = parse(origin) ?: return false
    if (uri.scheme == null || !uri.scheme.equals(here.scheme, ignoreCase = true)) return false
    val host = uri.host ?: return false
    val ours = here.host ?: return false
    if (host != ours && !(ours == local.host && host == "localhost")) return false
    return uri.port == here.port
  }

  private fun parse(url: String): URI? = try {
    URI(url)
  } catch (e: Exception) {
    null
  }
}
