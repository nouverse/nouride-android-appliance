package tech.nouverse.nouride.android.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteHostTest {
  @Test
  fun `an address typed without a scheme is the tunnel's, over https`() {
    assertEquals("https://nouride.example.com", RemoteHost.base("nouride.example.com"))
    assertEquals("https://nouride.example.com", RemoteHost.base("  nouride.example.com/  "))
    assertEquals("https://nouride.example.com", RemoteHost.base("https://nouride.example.com"))
    assertEquals("https://nouride.example.com", RemoteHost.base("HTTPS://nouride.example.com/"))
  }

  @Test
  fun `a port is kept, and the dashboard's own address becomes the machine it is on`() {
    assertEquals("https://nouride.example.com:8443", RemoteHost.base("https://nouride.example.com:8443"))
    assertEquals("https://nouride.example.com:8443", RemoteHost.base("nouride.example.com:8443"))
    // Pasted out of a chat, a dashboard link is the machine, not a path.
    assertEquals("https://nouride.example.com", RemoteHost.base("https://nouride.example.com/dashboard/"))
    assertEquals("https://nouride.example.com", RemoteHost.base("https://nouride.example.com/dashboard"))
    // A host that is only an address, as a tunnel's own subdomain is.
    assertEquals("https://box.tail1234.ts.net", RemoteHost.base("box.tail1234.ts.net"))
  }

  @Test
  fun `plain http is refused, and so is anything that is not an address`() {
    // The app speaks cleartext to its own daemon on the loopback and to nothing else.
    assertNull(RemoteHost.base("http://nouride.example.com"))
    assertNull(RemoteHost.base("http://192.168.50.20:18254"))
    assertNull(RemoteHost.base("ftp://nouride.example.com"))
    assertNull(RemoteHost.base(""))
    assertNull(RemoteHost.base("   "))
    assertNull(RemoteHost.base("nouride example.com"))
    assertNull(RemoteHost.base("https://nouride.example.com/agents"))
    assertNull(RemoteHost.base("https://nouride.example.com?token=1"))
    assertNull(RemoteHost.base("https://nouride.example.com#/agents"))
    assertNull(RemoteHost.base("https://"))
    assertNull(RemoteHost.base("https://" + "a".repeat(250) + ".example.com"))
  }

  @Test
  fun `the dashboard is the same path as the phone's own`() {
    assertEquals("https://nouride.example.com/dashboard/", RemoteHost.dashboard(RemoteHost.base("nouride.example.com")!!))
  }
}
