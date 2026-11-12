package tech.nouverse.nouride.android.server

import java.net.URI

/**
 * The address of a Nouride that runs on another machine, which this phone opens instead of running one itself: a
 * computer at home reached through a Cloudflare tunnel, or any other machine with an HTTPS address in front of it.
 *
 * A person is handed that address once and types it, so what is taken here is generous: with or without `https://`,
 * with a port, or with the dashboard's own address pasted straight out of a chat. `https` is assumed when nothing
 * says otherwise, because a tunnel is https and a bare name means the tunnel.
 *
 * `http` is refused. The app may speak plain HTTP to its own daemon on the loopback and to nothing else
 * (`res/xml/network_security_config.xml`), and a dashboard reached over the open internet is a login: a password on the
 * wire. A machine that is only ever reached over HTTPS in a tunnel is the case this is for. Pure, so every shape a
 * person may type is tested without a phone.
 */
object RemoteHost {
  /** The dashboard's own path. A pasted dashboard address becomes the machine it is on. */
  private const val DASHBOARD_PATH = "/dashboard"

  /** Long enough for a tunnel's own name, short enough that nothing else is in there. */
  private const val MAX = 200

  /**
   * The base URL of the Nouride at [typed], or null when it cannot be one: nothing typed, something with a space in
   * it, a scheme that is not http or https, a query or a fragment, or a path that is not the dashboard's.
   */
  fun base(typed: String): String? {
    val text = typed.trim()
    if (text.isEmpty() || text.length > MAX) return null
    if (text.any { it.isWhitespace() }) return null
    val uri = parse(if ("://" in text) text else "https://$text") ?: return null
    if (uri.scheme?.lowercase() != "https") return null
    val host = uri.host?.takeIf { it.isNotEmpty() } ?: return null
    val path = uri.path.orEmpty().removeSuffix("/")
    if (path.isNotEmpty() && path != DASHBOARD_PATH) return null
    if (uri.query != null || uri.fragment != null) return null
    val port = if (uri.port == -1) "" else ":${uri.port}"
    return "https://$host$port"
  }

  /** The dashboard at that machine, which is what this app opens: the same path as the phone's own. */
  fun dashboard(base: String): String = "$base$DASHBOARD_PATH/"

  private fun parse(text: String): URI? = try {
    URI(text)
  } catch (e: Exception) {
    null
  }
}
