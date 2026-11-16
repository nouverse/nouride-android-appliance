package tech.nouverse.nouride.android.server

import org.junit.Assert.assertEquals
import org.junit.Test

class BackoffTest {
  @Test
  fun `doubles from two seconds and stops at five minutes`() {
    val backoff = Backoff()
    val delays = (1..10).map { backoff.next(uptimeMs = 1_000) }
    assertEquals(listOf(2_000L, 4_000, 8_000, 16_000, 32_000, 64_000, 128_000, 256_000, 300_000, 300_000), delays)
  }

  @Test
  fun `a run of ten minutes or more starts the count again`() {
    val backoff = Backoff()
    repeat(5) { backoff.next(uptimeMs = 1_000) }
    assertEquals(2_000L, backoff.next(uptimeMs = 600_000))
    assertEquals(4_000L, backoff.next(uptimeMs = 599_999))
  }

  @Test
  fun `many stops in a row stay at the cap`() {
    val backoff = Backoff()
    repeat(100) { backoff.next(uptimeMs = 0) }
    assertEquals(300_000L, backoff.next(uptimeMs = 0))
  }
}
