package tech.nouverse.nouride.android.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkAccessTest {
  private val installed = "PORT=18254\nHOST=0.0.0.0\n"

  @Test
  fun `the installer's HOST lets other devices in, the loopback address keeps Nouride to the phone`() {
    assertTrue(NetworkAccess.open(installed))
    assertFalse(NetworkAccess.open("PORT=18254\nHOST=127.0.0.1\n"))
    assertFalse(NetworkAccess.open("HOST=\"localhost\""))
    // No line: Nouride's own default, the loopback address.
    assertFalse(NetworkAccess.open("PORT=18254\n"))
    assertTrue(NetworkAccess.open("HOST=192.168.1.23"))
  }

  @Test
  fun `the switch changes the HOST line and nothing else`() {
    assertEquals("PORT=18254\nHOST=127.0.0.1\n", NetworkAccess.withAccess(installed, open = false))
    assertEquals(installed, NetworkAccess.withAccess("PORT=18254\nHOST=127.0.0.1\n", open = true))
    // A file without one gets one, after a last line that had no newline.
    assertEquals("PORT=18254\nHOST=127.0.0.1\n", NetworkAccess.withAccess("PORT=18254", open = false))
    assertFalse(NetworkAccess.open(NetworkAccess.withAccess(installed, open = false)))
    assertTrue(NetworkAccess.open(NetworkAccess.withAccess(NetworkAccess.withAccess(installed, open = false), open = true)))
  }
}
