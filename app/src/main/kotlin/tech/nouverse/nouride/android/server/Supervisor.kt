package tech.nouverse.nouride.android.server

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * How long to wait before starting the daemon again after it stopped on its own: [baseMs], doubled
 * for each stop in a row, at most [maxMs]. A run that lasted [stableMs] or longer starts the
 * count again.
 */
class Backoff(
  private val baseMs: Long = 2_000,
  private val maxMs: Long = 300_000,
  private val stableMs: Long = 600_000,
) {
  private var failures = 0

  fun next(uptimeMs: Long): Long {
    if (uptimeMs >= stableMs) failures = 0
    val delay = if (failures >= 30) maxMs else minOf(maxMs, baseMs shl failures)
    failures++
    return delay
  }
}

/**
 * Keeps one daemon process running: [start] it, wait for it to end, and unless [stop] was called,
 * start it again after the [backoff] delay. [run] blocks; it belongs on a thread of its own.
 */
class Supervisor(
  private val start: () -> Process,
  private val backoff: Backoff,
  private val clock: () -> Long,
  private val onStarted: (Process) -> Unit,
  private val onExited: (exitCode: Int, restartInMs: Long?) -> Unit,
) {
  private val stopped = CountDownLatch(1)

  @Volatile
  private var stopping = false

  @Volatile
  private var restarting = false

  fun run() {
    while (!stopping) {
      val began = clock()
      val process = start()
      onStarted(process)
      val code = process.waitFor()
      if (stopping) {
        onExited(code, null)
        break
      }
      // An exit the app asked for: started again at once, and not counted as the daemon stopping on its own.
      if (restarting) {
        restarting = false
        onExited(code, 0)
        continue
      }
      val delay = backoff.next(clock() - began)
      onExited(code, delay)
      stopped.await(delay, TimeUnit.MILLISECONDS)
    }
  }

  /** The next exit is asked for (the app changed what the daemon reads when it starts): start it again at once. */
  fun restartNext() {
    restarting = true
  }

  fun stop() {
    stopping = true
    stopped.countDown()
  }
}
