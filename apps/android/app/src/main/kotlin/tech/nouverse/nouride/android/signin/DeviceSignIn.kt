package tech.nouverse.nouride.android.signin

import java.net.URI
import java.net.URLDecoder

/**
 * The dashboard signed in on the phone's own Nouride with a device token (Nouride 0.8.40): one kept in the app's
 * private preferences, made with the install's control token, traded for a session each time the dashboard opens and
 * each time it reaches its sign-in page. The rules both apps share are `spec/device-sign-in.md` and its cases
 * `spec/fixtures/device-sign-in.json`; the desktop implements the same in `crates/daemon/src/device_sign_in.rs`. Pure,
 * but for [round]'s [Effects], so the rules are tested without a phone ([LocalDevice] is the real one).
 */
object DeviceSignIn {
  const val SESSION = "nouride_session"
  const val LABEL_PREFIX = "Nouride Home"
  const val LABEL_MAX = 60
  const val ROUND_WAIT_MS = 120_000L
  const val MINT_WAIT_MS = 2_000L
  const val RETRY_AFTER_MS = 60_000L
  const val AGAIN_MS = 10_000L
  const val SIGN_IN_PATH = "/dashboard/login"

  /** `Nouride Home (<name>)`, which Nouride's Users page lists; only what Nouride takes in a label, at most 60. */
  fun label(name: String): String {
    val kept = name.map { c ->
      when {
        c == '’' || c == '‘' -> '\''
        (c in 'a'..'z') || (c in 'A'..'Z') || (c in '0'..'9') || c in " ._'()-" -> c
        else -> ' '
      }
    }.joinToString("")
    val words = kept.split(' ').filter { it.isNotEmpty() }.joinToString(" ")
    if (words.isEmpty()) return LABEL_PREFIX
    val room = LABEL_MAX - LABEL_PREFIX.length - " ()".length
    return "$LABEL_PREFIX (${words.take(room).trimEnd()})"
  }

  enum class Call { TRADE, MINT }

  /** What a call answered; [code] 0 when nothing answered. */
  data class Answer(
    val code: Int,
    val retryAfter: String? = null,
    /** A trade's `nouride_session=<token>`. */
    val cookie: String? = null,
    /** A mint's token, `ndev_…`. */
    val value: String? = null,
  )

  sealed class Step {
    object SignedIn : Step()
    object Trade : Step()
    object Mint : Step()
    data class Wait(val ms: Long, val then: Call) : Step()
    object GiveUp : Step()
  }

  /** What an answer leads to; [minted] when this round has made a token already (the spec's table). */
  fun next(call: Call, answer: Answer, minted: Boolean): Step = when {
    call == Call.TRADE && answer.code == 200 && answer.cookie != null -> Step.SignedIn
    // Refused: a new token, once. Never this one again, nor a new one twice: a failed trade spends the sign-in
    // allowance the person's own password shares.
    call == Call.TRADE && answer.code == 401 && !minted -> Step.Mint
    call == Call.MINT && answer.code == 201 && answer.value != null -> Step.Trade
    // No admin yet: a fresh install's first start is still making it.
    call == Call.MINT && answer.code == 409 -> Step.Wait(MINT_WAIT_MS, Call.MINT)
    answer.code == 429 -> Step.Wait(retryAfterMs(answer.retryAfter), call)
    else -> Step.GiveUp
  }

  private fun retryAfterMs(header: String?): Long = header?.trim()?.toLongOrNull()?.times(1000) ?: RETRY_AFTER_MS

  /** What a round does to the world, so a test can stand in for it. */
  interface Effects {
    fun trade(token: String): Answer
    fun mint(): Answer

    /** Keeps a new token in place of the old. */
    fun keep(token: String)
    fun waitFor(ms: Long)
  }

  sealed class Outcome {
    /** `nouride_session=<token>`, for the WebView. */
    data class SignedIn(val cookie: String) : Outcome()

    /** The last answer's code: the dashboard shows what it shows. */
    data class GaveUp(val code: Int) : Outcome()
  }

  /** One round: a trade of the kept token, or a mint first when none is kept, until signed in or given up. */
  fun round(kept: String?, fx: Effects): Outcome {
    var token = kept
    var minted = false
    var waited = 0L
    var call = if (token != null) Call.TRADE else Call.MINT
    while (true) {
      val answer = if (call == Call.TRADE) fx.trade(token.orEmpty()) else fx.mint()
      call = when (val step = next(call, answer, minted)) {
        Step.SignedIn -> return Outcome.SignedIn(answer.cookie.orEmpty())
        Step.GiveUp -> return Outcome.GaveUp(answer.code)
        Step.Mint -> Call.MINT
        Step.Trade -> {
          val value = answer.value.orEmpty()
          fx.keep(value)
          token = value
          minted = true
          Call.TRADE
        }
        is Step.Wait -> {
          if (waited + step.ms > ROUND_WAIT_MS) return Outcome.GaveUp(answer.code)
          fx.waitFor(step.ms)
          waited += step.ms
          step.then
        }
      }
    }
  }

  /** Whether [url] is the local dashboard's sign-in page. */
  fun signInPage(url: String, origin: String): Boolean {
    val (u, o) = try {
      URI(url) to URI(origin)
    } catch (e: Exception) {
      return false
    }
    if (u.scheme == null || u.host == null) return false
    return u.scheme.equals(o.scheme, true) && u.host.equals(o.host, true) && port(u) == port(o) &&
      (u.rawPath == SIGN_IN_PATH || u.rawPath == "$SIGN_IN_PATH/")
  }

  private fun port(u: URI): Int = if (u.port != -1) u.port else if (u.scheme.equals("https", true)) 443 else 80

  /**
   * Where the WebView goes once signed in again from the sign-in page [url]: the page its `next` names, checked as the
   * dashboard's own `safeNext` checks it, or the dashboard's first page.
   */
  fun afterSignIn(url: String, origin: String): String {
    val base = origin.trimEnd('/')
    val query = try {
      URI(url).rawQuery
    } catch (e: Exception) {
      null
    }
    val next = query?.split('&')?.firstOrNull { it.substringBefore('=') == "next" }
      ?.let { URLDecoder.decode(it.substringAfter('=', ""), "UTF-8") }
    return if (next != null && safeNext(next)) "$base/dashboard$next" else "$base/dashboard/"
  }

  private fun safeNext(next: String): Boolean {
    if (!next.startsWith("/") || next.startsWith("//") || next.any { it == '\\' || it.isISOControl() }) return false
    val path = next.split('?', '#').first().lowercase()
    return path != "/login" && !path.startsWith("/login/")
  }

  /**
   * Whether a round on opening that gave up after [code] holds off the next from the sign-in page ([again]): only when
   * Nouride answered. Nothing answering means it is still starting, and the round after Try again should sign in.
   */
  fun gaveUpCounts(code: Int): Boolean = code != 0

  /**
   * Whether a round may start from the sign-in page now, [last] being when the last round from it, or the last on
   * opening that gave up, started (ms since the epoch).
   */
  fun again(last: Long?, now: Long): Boolean = last == null || now - last >= AGAIN_MS

  /** The session cookie, `nouride_session=<token>`, from a trade's `Set-Cookie` headers; null when there is none. */
  fun sessionCookie(setCookie: List<String>): String? =
    setCookie.map { it.substringBefore(';').trim() }.firstOrNull { it.startsWith("$SESSION=") && it.length > SESSION.length + 1 }
}
