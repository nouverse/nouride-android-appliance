package tech.nouverse.nouride.android.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupProgressTest {
  @Test
  fun `unpacking moves by the bytes read`() {
    val p = SetupProgress()
    p.unpacked(0, 1000)
    assertEquals(Progress(Step.UNPACK, 0), p.snapshot())
    p.unpacked(500, 1000)
    assertEquals(5, p.snapshot().percent)
    p.unpacked(1000, 1000)
    assertEquals(10, p.snapshot().percent)
  }

  @Test
  fun `a marker starts its step, and the bar jumps to its slice`() {
    val p = SetupProgress()
    p.line(SetupProgress.marker(Step.PACKAGES))
    assertEquals(Progress(Step.PACKAGES, 10), p.snapshot())
    p.line("  " + SetupProgress.marker(Step.NOURIDE) + "  ")
    assertEquals(Progress(Step.NOURIDE, 75), p.snapshot())
    p.starting()
    assertEquals(Progress(Step.START, 95), p.snapshot())
  }

  @Test
  fun `lines nudge the bar toward the end of the step, never past it`() {
    val p = SetupProgress()
    p.line(SetupProgress.marker(Step.TOOLS))
    p.line("Get:1 http://deb.debian.org bookworm/main curl")
    val one = p.snapshot().percent
    assertTrue("a line moves the bar, got $one", one > 40)
    repeat(10_000) { p.line("Setting up something") }
    assertEquals(Progress(Step.TOOLS, 74), p.snapshot())
    p.line("")
    assertEquals(74, p.snapshot().percent)
  }

  @Test
  fun `the bar never goes back, not even for an earlier marker`() {
    val p = SetupProgress()
    p.line(SetupProgress.marker(Step.NOURIDE))
    p.line(SetupProgress.marker(Step.PACKAGES))
    p.unpacked(1, 2)
    assertEquals(Progress(Step.NOURIDE, 75), p.snapshot())
  }

  @Test
  fun `it never says 100 before Nouride answers`() {
    val p = SetupProgress()
    p.starting()
    repeat(10_000) { p.line("booting") }
    assertEquals(98, p.snapshot().percent)
  }
}
