package tech.nouverse.nouride.android.update

import org.junit.Assert.assertEquals
import org.junit.Test
import tech.nouverse.nouride.android.update.StallWatch.Verdict

class StallWatchTest {
  @Test
  fun `a download that moves is left alone, however slow`() {
    val watch = StallWatch(stallMs = 30_000, maxRestarts = 2)
    for (second in 0..120) assertEquals(Verdict.MOVING, watch.seen(received = second * 10L, nowMs = second * 1_000L))
  }

  @Test
  fun `one that stops is restarted once it has been quiet for the window, not before`() {
    val watch = StallWatch(stallMs = 30_000, maxRestarts = 2)
    assertEquals(Verdict.MOVING, watch.seen(0, 0))
    assertEquals(Verdict.MOVING, watch.seen(0, 29_999))
    assertEquals(Verdict.RESTART, watch.seen(0, 30_000))
    // The window starts again from the restart: not a restart every second after.
    assertEquals(Verdict.MOVING, watch.seen(0, 31_000))
    assertEquals(Verdict.MOVING, watch.seen(0, 59_999))
  }

  @Test
  fun `bytes after a restart start the window again`() {
    val watch = StallWatch(stallMs = 30_000, maxRestarts = 1)
    watch.seen(0, 0)
    assertEquals(Verdict.RESTART, watch.seen(0, 30_000))
    assertEquals(Verdict.MOVING, watch.seen(5_000, 31_000))
    assertEquals(Verdict.MOVING, watch.seen(5_000, 60_999))
  }

  @Test
  fun `it gives up after its restarts, rather than waiting for ever`() {
    val watch = StallWatch(stallMs = 30_000, maxRestarts = 2)
    watch.seen(100, 0)
    assertEquals(Verdict.RESTART, watch.seen(100, 30_000))
    assertEquals(Verdict.RESTART, watch.seen(100, 60_000))
    assertEquals(Verdict.GIVE_UP, watch.seen(100, 90_000))
  }
}
