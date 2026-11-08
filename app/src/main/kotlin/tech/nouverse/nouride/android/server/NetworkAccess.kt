package tech.nouverse.nouride.android.server

/**
 * Whether other devices on the Wi-Fi may open Nouride, as the install's `.env` says it: `HOST=0.0.0.0` answers them,
 * `HOST=127.0.0.1` only this phone (the app's own dashboard is always on the loopback address). `nouride start` reads
 * its `.env` each time, and `HOST` there wins over `config.toml`, so the Connect tab's switch is this line and a
 * restart. Without the line, Nouride's own default holds: the loopback address. Pure.
 */
object NetworkAccess {
  private const val KEY = "HOST="
  const val EVERYONE = "0.0.0.0"
  const val THIS_PHONE = "127.0.0.1"

  fun open(env: String): Boolean {
    val host = env.split("\n").map { it.trim() }.lastOrNull { it.startsWith(KEY) }
      ?.removePrefix(KEY)?.trim()?.trim('"', '\'') ?: return false
    return host.isNotEmpty() && host != THIS_PHONE && host != "localhost" && host != "::1"
  }

  /** The `.env` with its `HOST` set for [open]; every other line as it was. */
  fun withAccess(env: String, open: Boolean): String {
    val line = KEY + if (open) EVERYONE else THIS_PHONE
    val lines = env.split("\n")
    if (lines.any { it.trim().startsWith(KEY) }) return lines.joinToString("\n") { if (it.trim().startsWith(KEY)) line else it }
    return (if (env.isEmpty() || env.endsWith("\n")) env else "$env\n") + line + "\n"
  }
}
