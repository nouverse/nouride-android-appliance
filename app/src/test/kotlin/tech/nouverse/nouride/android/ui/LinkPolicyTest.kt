package tech.nouverse.nouride.android.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.nouverse.nouride.android.Nouride

class LinkPolicyTest {
  private val dashboard = Nouride.DASHBOARD_URL
  private fun page(path: String) = "${Nouride.ORIGIN}/dashboard/$path"

  @Test
  fun `Back goes to the dashboard page before, as a browser does`() {
    val history = listOf(dashboard, page("agents"), page("agents/nouva"))
    assertTrue(LinkPolicy.backInside(history, current = 2))
    assertTrue(LinkPolicy.backInside(history, current = 1))
  }

  @Test
  fun `from the first page Back leaves for the app`() {
    assertFalse(LinkPolicy.backInside(listOf(dashboard), current = 0))
    assertFalse(LinkPolicy.backInside(listOf("about:blank", dashboard), current = 1))
  }

  @Test
  fun `never back onto the sign-in, the first password or the setup wizard`() {
    for (once in listOf("login", "login?next=%2Fagents", "welcome", "setup", "oauth/mcp/callback?code=x")) {
      assertFalse(once, LinkPolicy.backInside(listOf(page(once), dashboard), current = 1))
    }
    // A page that only starts with the same letters is an ordinary one.
    assertTrue(LinkPolicy.backInside(listOf(page("setupguide"), dashboard), current = 1))
  }

  @Test
  fun `never out of Nouride`() {
    assertFalse(LinkPolicy.backInside(listOf("https://accounts.google.com/o/oauth2", dashboard), current = 1))
  }
}
