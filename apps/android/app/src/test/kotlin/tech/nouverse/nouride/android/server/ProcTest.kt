package tech.nouverse.nouride.android.server

import org.junit.Assert.assertEquals
import org.junit.Test

class ProcTest {
  @Test
  fun `the parent pid after a name with spaces and parentheses`() {
    assertEquals(4120, Proc.parsePpid("4133 (nouride (x) y) S 4120 4133 4120 0 -1 4194560"))
  }

  @Test
  fun `descendants in breadth-first order`() {
    val entries = listOf(
      Proc.Entry(10, 1, listOf("libproot.so")),
      Proc.Entry(11, 10, listOf("/bin/su")),
      Proc.Entry(12, 11, listOf("./nouride", "start")),
      Proc.Entry(13, 12, listOf("bash")),
      Proc.Entry(20, 1, listOf("other")),
    )
    assertEquals(listOf(11, 12, 13), Proc.descendants(entries, 10).map { it.pid })
  }
}
