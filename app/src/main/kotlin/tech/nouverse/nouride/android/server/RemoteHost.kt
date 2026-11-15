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

  private const val HTTPS_PORT = 443

  /** What a person typed, read: an address to open, or why it cannot be one, each with its own sentence on screen. */
  sealed class Read {
    data class Address(val base: String) : Read()

    /** Nothing typed. */
    object Empty : Read()

    /** `http://`: the one refusal a person can fix by typing `https://` instead, so it is said on its own. */
    object PlainHttp : Read()

    /** Something with a space in it, another scheme, a query or a fragment, a path that is not the dashboard's. */
    object NotAnAddress : Read()
  }

  /** The base URL of the Nouride at [typed], or why there is none. */
  fun read(typed: String): Read {
    val text = typed.trim()
    if (text.isEmpty()) return Read.Empty
    if (text.length > MAX || text.any { it.isWhitespace() }) return Read.NotAnAddress
    val uri = parse(if ("://" in text) text else "https://$text") ?: return Read.NotAnAddress
    val scheme = uri.scheme?.lowercase()
    if (scheme == "http") return Read.PlainHttp
    if (scheme != "https") return Read.NotAnAddress
    val host = uri.host?.takeIf { it.isNotEmpty() }?.lowercase() ?: return Read.NotAnAddress
    val path = uri.path.orEmpty().removeSuffix("/")
    if (path.isNotEmpty() && path != DASHBOARD_PATH) return Read.NotAnAddress
    if (uri.query != null || uri.fragment != null) return Read.NotAnAddress
    // 443 is https's own: kept, the origin would not match the links the dashboard writes without it (`LinkPolicy`).
    val port = if (uri.port == -1 || uri.port == HTTPS_PORT) "" else ":${uri.port}"
    return Read.Address("https://$host$port")
  }

  /** The base URL of the Nouride at [typed], or null when it cannot be one. */
  fun base(typed: String): String? = (read(typed) as? Read.Address)?.base

  /** How an address is shown to a person: the name they typed, without the scheme every one of them has. */
  fun shown(base: String): String = base.removePrefix("https://")

  /** The dashboard at that machine, which is what this app opens: the same path as the phone's own. */
  fun dashboard(base: String): String = "$base$DASHBOARD_PATH/"

  private fun parse(text: String): URI? = try {
    URI(text)
  } catch (e: Exception) {
    null
  }
}
