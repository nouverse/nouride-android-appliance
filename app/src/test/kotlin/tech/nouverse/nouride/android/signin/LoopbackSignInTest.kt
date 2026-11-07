package tech.nouverse.nouride.android.signin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.nouverse.nouride.android.Nouride
import tech.nouverse.nouride.android.ui.LinkPolicy

class LoopbackSignInTest {
  private val google = "https://accounts.google.com/o/oauth2/v2/auth?client_id=x&redirect_uri=http%3A%2F%2Flocalhost%3A3000%2Foauth-callback&response_type=code"
  private val openai = "https://auth.openai.com/oauth/authorize?response_type=code&redirect_uri=http%3A%2F%2Flocalhost%3A1455%2Fauth%2Fcallback&state=s%2Bt"

  @Test
  fun `a provider page with a redirect the app can catch is expected, any other link is not`() {
    val antigravity = LoopbackSignIn.expect(google, now = 100)!!
    assertEquals("antigravity", antigravity.route.provider)
    assertNull(antigravity.state)
    val codex = LoopbackSignIn.expect(openai, now = 100)!!
    assertEquals(1455, codex.route.port)
    assertEquals("s+t", codex.state)
    assertNull(LoopbackSignIn.expect("https://example.com/?redirect_uri=http%3A%2F%2Flocalhost%3A9999%2Fx", 0))
    assertNull(LoopbackSignIn.expect("https://nouride.com/docs", 0))
  }

  @Test
  fun `the browser's request gives the code, on the route's own path only`() {
    val route = LoopbackSignIn.ROUTES.first { it.provider == "codex" }
    assertEquals(LoopbackSignIn.Callback("a/b", "s+t"), LoopbackSignIn.callback(route, "/auth/callback?code=a%2Fb&state=s%2Bt"))
    assertNull(LoopbackSignIn.callback(route, "/favicon.ico"))
    assertNull(LoopbackSignIn.callback(route, "/auth/callback?error=access_denied"))
    assertNull(LoopbackSignIn.callback(route, "/auth/callback?code="))
  }

  @Test
  fun `only the sign-in the app opened is accepted, in time, with its state`() {
    val expected = LoopbackSignIn.expect(openai, now = 1_000)!!
    assertTrue(LoopbackSignIn.accepts(expected, LoopbackSignIn.Callback("c", "s+t"), now = 2_000))
    // Another app's made-up code carries no state of ours.
    assertFalse(LoopbackSignIn.accepts(expected, LoopbackSignIn.Callback("c", "forged"), now = 2_000))
    assertFalse(LoopbackSignIn.accepts(expected, LoopbackSignIn.Callback("c", null), now = 2_000))
    assertFalse(LoopbackSignIn.accepts(expected, LoopbackSignIn.Callback("c", "s+t"), now = 1_000 + LoopbackSignIn.WINDOW_MS + 1))
  }

  @Test
  fun `the code goes to the gateway the way the paste box sends it`() {
    val route = LoopbackSignIn.ROUTES.first { it.provider == "antigravity" }
    assertEquals("http://127.0.0.1:${Nouride.PORT}/llm-gateway/api/providers/antigravity/accounts/add", LoopbackSignIn.addAccountUrl(route))
    assertEquals("""{"code":"c"}""", LoopbackSignIn.addAccountBody(LoopbackSignIn.Callback("c", null)))
    assertEquals("""{"code":"c","state":"s"}""", LoopbackSignIn.addAccountBody(LoopbackSignIn.Callback("c", "s")))
  }

  @Test
  fun `the WebView keeps Nouride's own pages and sends every other link out`() {
    val port = Nouride.PORT
    assertTrue(LinkPolicy.inApp("http://127.0.0.1:$port/dashboard/"))
    assertTrue(LinkPolicy.inApp("http://localhost:$port/llm-gateway/oauth/start"))
    assertTrue(LinkPolicy.inApp("about:blank"))
    assertFalse(LinkPolicy.inApp("https://accounts.google.com/o/oauth2/v2/auth"))
    // The gateway's own port, and another copy of the app's Nouride, are not this dashboard.
    assertFalse(LinkPolicy.inApp("http://127.0.0.1:${Nouride.GATEWAY_PORT}/"))
    assertFalse(LinkPolicy.inApp("http://127.0.0.1:${if (port == 18254) 18264 else 18254}/dashboard/"))
    assertFalse(LinkPolicy.inApp("https://127.0.0.1:$port/dashboard/"))
    assertFalse(LinkPolicy.inApp("http://127.0.0.1.evil.com:$port/"))
    assertFalse(LinkPolicy.inApp("mailto:someone@example.com"))
  }

  @Test
  fun `the dashboard's way back and its user agent mark are the ones it looks for`() {
    // commons/lib/home-app.ts in Nouride: HOME_APP_BACK, and the mark it finds in the user agent.
    assertEquals("nouridehome://home", LinkPolicy.HOME)
    assertTrue(LinkPolicy.USER_AGENT_MARK.startsWith("NourideHome/"))
    assertFalse(LinkPolicy.inApp(LinkPolicy.HOME))
  }
}
