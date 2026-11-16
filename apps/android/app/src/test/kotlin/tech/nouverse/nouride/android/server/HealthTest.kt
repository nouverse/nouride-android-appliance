package tech.nouverse.nouride.android.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthTest {
  @Test
  fun `ok is a 200 with status ok`() {
    val health = Health.parse(200, """{"status":"ok","edition":"basic","uptime":42,"version":"0.8.18","agents":1}""")
    assertTrue(health.ok)
    assertEquals("0.8.18", health.version)
    assertEquals(42L, health.uptimeSeconds)
    assertEquals("ok, version 0.8.18, up 42s", health.summary())
  }

  @Test
  fun `starting and degraded are 503 and not ok`() {
    val starting = Health.parse(503, """{"status":"starting","uptime":1,"version":"0.8.18"}""")
    assertFalse(starting.ok)
    assertEquals("starting", starting.status)
    val degraded = Health.parse(503, """{"status":"degraded","uptime":9,"version":"0.8.18","issues":["telegram: dead","x"]}""")
    assertFalse(degraded.ok)
    assertEquals(listOf("telegram: dead", "x"), degraded.issues)
  }

  @Test
  fun `a 200 that does not say ok is not ok`() {
    assertFalse(Health.parse(200, """{"status":"shutting_down"}""").ok)
    assertFalse(Health.parse(200, "<html>proxy</html>").ok)
  }

  @Test
  fun `a body that is not JSON is reported, not thrown`() {
    val health = Health.parse(502, "Bad Gateway")
    assertNull(health.status)
    assertEquals("not JSON: Bad Gateway", health.error)
  }
}
