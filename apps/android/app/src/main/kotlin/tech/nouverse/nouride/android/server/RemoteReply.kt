package tech.nouverse.nouride.android.server

/**
 * What the other computer's Nouride said when this phone asked its `/health`, in the three answers Home acts on: it
 * answers, it does not, or something stands in front of it that the dashboard's own page will show (a sign-in such as
 * Cloudflare Access).
 */
enum class Reply { ANSWERS, DOWN, UNSURE }

object RemoteReply {
  /**
   * Pure, so each shape of answer is tested. No answer at all (no such name, no connection, no TLS, a timeout) is down,
   * and so is a 5xx without Nouride's own body: a tunnel whose computer is off answers 502 or 530 from Cloudflare.
   * Nouride's own body is Nouride answering, whatever its status: the dashboard says the rest.
   */
  fun of(health: Health): Reply = when {
    health.httpCode == 0 -> Reply.DOWN
    health.status != null -> Reply.ANSWERS
    health.httpCode >= 500 -> Reply.DOWN
    else -> Reply.UNSURE
  }

  /** The other computer's `/health`, beside its dashboard. */
  fun healthUrl(base: String): String = "$base/health"
}
