package tech.nouverse.nouride.android.update

/**
 * Whether Nouride's download of an update is still moving, from what it reports once a second. Nouride up to 0.8.26
 * waits on a quiet connection for ever: on the owner's phone (2026-11-09) one sat at 0 bytes on an open connection
 * to GitHub while the phone's own `curl` fetched the same file in 16 seconds, and the update before it took 42 minutes.
 * A pause and a resume opens a new connection and goes on from the bytes already on disk, so a download that has not
 * moved for [stallMs] is restarted that way, at most [maxRestarts] times, before it is said to have failed. Pure.
 */
class StallWatch(private val stallMs: Long, private val maxRestarts: Int) {
  enum class Verdict { MOVING, RESTART, GIVE_UP }

  private var last = -1L
  private var since = 0L
  private var restarts = 0

  /** [received]: the bytes Nouride has, now; [nowMs]: a clock that only goes forward. */
  fun seen(received: Long, nowMs: Long): Verdict {
    if (received != last) {
      last = received
      since = nowMs
      return Verdict.MOVING
    }
    if (nowMs - since < stallMs) return Verdict.MOVING
    if (restarts >= maxRestarts) return Verdict.GIVE_UP
    restarts++
    since = nowMs
    return Verdict.RESTART
  }
}
