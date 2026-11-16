package tech.nouverse.nouride.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SupportTextTest {
  @Test
  fun `the summary is one fact a line, in the order given`() {
    assertEquals("state: running\nhealth: ok", SupportText.summary(listOf("state" to "running", "health" to "ok")))
  }

  @Test
  fun `the log keeps its newest lines, the last one at the end`() {
    assertEquals("c\nd", SupportText.tail("a\nb\nc\nd\n", 2))
    assertEquals("a", SupportText.tail("a\n", 5))
  }

  @Test
  fun `the report puts each log under its name, and says when one is empty`() {
    val report = SupportText.report("state: running", listOf("service.log" to "started", "daemon.log" to ""))
    assertEquals("state: running\n\n== service.log ==\nstarted\n\n== daemon.log ==\n(empty)", report)
  }

  @Test
  fun `a long log is read from its end, never starting on half a line`() {
    val file = File.createTempFile("support", ".log")
    try {
      file.writeText((1..2000).joinToString("\n") { "line number $it" } + "\n")
      val tail = SupportText.readTail(file, lines = 3, bytes = 200)
      assertEquals("line number 1998\nline number 1999\nline number 2000", tail)
      val all = SupportText.readTail(file, lines = 10_000, bytes = 200)
      assertTrue(all, all.lines().all { it.startsWith("line number ") })
      assertEquals("", SupportText.readTail(File(file.path + ".missing"), 3))
    } finally {
      file.delete()
    }
  }
}
