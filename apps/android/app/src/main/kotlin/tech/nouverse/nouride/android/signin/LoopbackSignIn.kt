package tech.nouverse.nouride.android.signin

import org.json.JSONObject
import tech.nouverse.nouride.android.Nouride
import java.net.URI
import java.net.URLDecoder

/**
 * Signing in to a model account (Google for Antigravity, ChatGPT for Codex) from the dashboard on the phone.
 *
 * The provider's page opens in the phone's browser, where the person is already signed in to Google; inside the app's
 * WebView Google refuses the sign-in, and the browser's session is not there anyway. These providers send the browser
 * back to a fixed `localhost` address the LLM gateway does not listen on: on a computer the person pastes that address
 * into the dashboard. On the phone the app listens there itself, for one sign-in, and hands the code to the gateway
 * the way the paste box does (`accounts/add` with the code and state), so nothing is pasted.
 *
 * Only a sign-in the app itself opened is accepted: within [WINDOW_MS], once, and with the same `state` when the
 * provider sends one. Any app on the phone can reach a loopback port, and a code it made up would otherwise add an
 * account of its choosing to the person's gateway. Pure, so the rules are tested without a phone.
 */
object LoopbackSignIn {
  /** A provider's fixed redirect: the port and path the app listens on, and the gateway's provider id. */
  data class Route(val provider: String, val port: Int, val path: String) {
    val redirectUri get() = "http://localhost:$port$path"
  }

  /** The sign-in the app opened and is waiting for. */
  data class Expected(val route: Route, val state: String?, val openedAt: Long)

  data class Callback(val code: String, val state: String?)

  /** Each as the gateway writes it (`antigravity/upstream/headers.ts`, `codex/index.ts` in Nougate). */
  val ROUTES = listOf(
    Route("antigravity", 3000, "/oauth-callback"),
    Route("codex", 1455, "/auth/callback"),
  )

  const val WINDOW_MS = 15 * 60 * 1000L

  /** The sign-in a provider's page is for, when its `redirect_uri` is one the app can catch; null for any other link. */
  fun expect(authorizeUrl: String, now: Long): Expected? {
    val query = query(authorizeUrl) ?: return null
    val route = ROUTES.firstOrNull { it.redirectUri == query["redirect_uri"] } ?: return null
    return Expected(route, query["state"], now)
  }

  /** The code in the browser's request for [route], from its request target (`/oauth-callback?code=...`). */
  fun callback(route: Route, target: String): Callback? {
    if (target.substringBefore('?') != route.path) return null
    val query = query("http://localhost$target") ?: return null
    val code = query["code"]?.takeIf { it.isNotBlank() } ?: return null
    return Callback(code, query["state"])
  }

  fun accepts(expected: Expected, callback: Callback, now: Long): Boolean =
    now - expected.openedAt in 0..WINDOW_MS && (expected.state == null || expected.state == callback.state)

  fun addAccountUrl(route: Route) = "${Nouride.ORIGIN}/llm-gateway/api/providers/${route.provider}/accounts/add"

  fun addAccountBody(callback: Callback): String =
    JSONObject().put("code", callback.code).apply { if (callback.state != null) put("state", callback.state) }.toString()

  private fun query(url: String): Map<String, String>? {
    val raw = try {
      URI(url).rawQuery
    } catch (e: Exception) {
      return null
    } ?: return emptyMap()
    return raw.split('&').filter { it.isNotEmpty() }.associate { pair ->
      val key = pair.substringBefore('=')
      val value = if ('=' in pair) pair.substringAfter('=') else ""
      decode(key) to decode(value)
    }
  }

  private fun decode(text: String) = URLDecoder.decode(text, "UTF-8")
}
