package tech.nouverse.nouride.android.ui

import org.junit.Assert.assertEquals
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
  fun `never a page back from the first password, which the page before sends the person back to`() {
    assertFalse(LinkPolicy.backInside(listOf(dashboard, page("welcome")), current = 1))
    assertEquals(LinkPolicy.Back.ASK, LinkPolicy.back(listOf(dashboard, page("welcome")), current = 1, dashboardShown = true))
    assertFalse(LinkPolicy.backInside(listOf(page("agents"), page("login?next=%2Fagents")), current = 1))
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

  @Test
  fun `Back that would close the dashboard asks first, and one that goes back a page does not`() {
    assertEquals(LinkPolicy.Back.PAGE, LinkPolicy.back(listOf(dashboard, page("agents")), current = 1, dashboardShown = true))
    assertEquals(LinkPolicy.Back.ASK, LinkPolicy.back(listOf(dashboard), current = 0, dashboardShown = true))
    // Coming back from the wizard's last step: Back would leave, so it asks too.
    assertEquals(LinkPolicy.Back.ASK, LinkPolicy.back(listOf(page("setup"), dashboard), current = 1, dashboardShown = true))
  }

  @Test
  fun `over the cat or while it opens, Back leaves without asking`() {
    assertEquals(LinkPolicy.Back.LEAVE, LinkPolicy.back(listOf(dashboard, page("agents")), current = 1, dashboardShown = false))
    assertEquals(LinkPolicy.Back.LEAVE, LinkPolicy.back(listOf(dashboard), current = 0, dashboardShown = false))
  }

  @Test
  fun `the header's back is the phone's Back, and the mark says the app catches it`() {
    assertEquals(LinkPolicy.Link.BACK, LinkPolicy.link(LinkPolicy.BACK, isForMainFrame = true))
    // The dashboard sends `nouridehome://back` only to an app that says 2 or more.
    assertEquals("NourideHome/2", LinkPolicy.USER_AGENT_MARK)
  }

  @Test
  fun `Back to Nouride Home is never the header's back`() {
    assertEquals(LinkPolicy.Link.LEAVE, LinkPolicy.link(LinkPolicy.HOME, isForMainFrame = true))
    assertEquals(LinkPolicy.Link.LEAVE, LinkPolicy.link(LinkPolicy.HOME, isForMainFrame = true, origin = "https://nouride.example.com"))
  }

  @Test
  fun `Back to Nouride Home asks on a deeper page, where Back goes a page back, and on the first page`() {
    assertEquals(LinkPolicy.Back.PAGE, LinkPolicy.back(listOf(dashboard, page("agents")), current = 1, dashboardShown = true))
    assertEquals(LinkPolicy.Back.ASK, LinkPolicy.leave(dashboardShown = true))
    assertEquals(LinkPolicy.Back.ASK, LinkPolicy.back(listOf(dashboard), current = 0, dashboardShown = true))
    assertEquals(LinkPolicy.Back.ASK, LinkPolicy.leave(dashboardShown = true))
  }

  @Test
  fun `over the cat or while it opens, Back to Nouride Home leaves without asking`() {
    assertEquals(LinkPolicy.Back.LEAVE, LinkPolicy.leave(dashboardShown = false))
  }

  @Test
  fun `the other machine's pages open here, and the phone's own are outside`() {
    val remote = "https://nouride.example.com"
    assertEquals(LinkPolicy.Link.HERE, LinkPolicy.link("$remote/dashboard/agents", isForMainFrame = true, origin = remote))
    assertEquals(LinkPolicy.Link.OUTSIDE, LinkPolicy.link(dashboard, isForMainFrame = true, origin = remote))
    // A tunnel answers over https, and that is the scheme that machine is known by.
    assertTrue(LinkPolicy.inApp("$remote/dashboard/", remote))
    assertFalse(LinkPolicy.inApp("https://elsewhere.example.com/dashboard/", remote))
    // The same name on another port is another server.
    assertFalse(LinkPolicy.inApp("https://nouride.example.com:8443/dashboard/", remote))
    // Back inside it goes back a page, and off its first page it asks, as on the phone's own.
    assertTrue(LinkPolicy.backInside(listOf("$remote/dashboard/", "$remote/dashboard/agents"), current = 1, origin = remote))
    assertFalse(LinkPolicy.backInside(listOf("$remote/dashboard/"), current = 0, origin = remote))
    assertEquals(LinkPolicy.Back.ASK, LinkPolicy.back(listOf("$remote/dashboard/"), current = 0, dashboardShown = true, origin = remote))
  }

  @Test
  fun `Nouride's pages open here, and anything else outside`() {
    assertEquals(LinkPolicy.Link.HERE, LinkPolicy.link(page("agents"), isForMainFrame = true))
    assertEquals(LinkPolicy.Link.OUTSIDE, LinkPolicy.link("https://accounts.google.com/o/oauth2", isForMainFrame = true))
    // A frame inside a page is the page's business.
    assertEquals(LinkPolicy.Link.HERE, LinkPolicy.link("https://www.youtube.com/embed/x", isForMainFrame = false))
  }
}
