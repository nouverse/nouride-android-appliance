package tech.nouverse.nouride.android.server

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteReplyTest {
  @Test
  fun `Nouride's own answer is Nouride answering, starting or not`() {
    assertEquals(Reply.ANSWERS, RemoteReply.of(Health.parse(200, """{"status":"ok","version":"0.8.38"}""")))
    assertEquals(Reply.ANSWERS, RemoteReply.of(Health.parse(503, """{"status":"starting"}""")))
  }

  @Test
  fun `no answer, or a tunnel whose computer is off, is down`() {
    assertEquals(Reply.DOWN, RemoteReply.of(Health.unreachable("java.net.UnknownHostException: nouride.example.com")))
    assertEquals(Reply.DOWN, RemoteReply.of(Health.parse(530, "<html>error code: 1033</html>")))
    assertEquals(Reply.DOWN, RemoteReply.of(Health.parse(502, "<html>Bad gateway</html>")))
  }

  @Test
  fun `a page in front of Nouride is neither, the dashboard shows it`() {
    // Cloudflare Access's sign-in, followed to its own page.
    assertEquals(Reply.UNSURE, RemoteReply.of(Health.parse(200, "<html>Sign in</html>")))
    assertEquals(Reply.UNSURE, RemoteReply.of(Health.parse(403, "Forbidden")))
  }

  @Test
  fun `health is beside the dashboard`() {
    assertEquals("https://nouride.example.com/health", RemoteReply.healthUrl("https://nouride.example.com"))
  }
}
