package tech.nouverse.nouride.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.nouverse.nouride.android.Nouride

class DashboardAimTest {
  private val remote = "https://nouride.example.com"

  @Test
  fun `this phone's Nouride is the loopback, signed in by the app`() {
    val aim = DashboardAim.of(null)
    assertEquals(Nouride.DASHBOARD_URL, aim.start)
    assertEquals(Nouride.ORIGIN, aim.origin)
    assertTrue(aim.local)
  }

  @Test
  fun `another machine's is its own dashboard, not signed in by the app`() {
    val aim = DashboardAim.of(remote)
    assertEquals("$remote/dashboard/", aim.start)
    assertEquals(remote, aim.origin)
    assertFalse(aim.local)
  }

  @Test
  fun `opened again after Nouride moved to another machine, the screen shows that machine`() {
    assertEquals(DashboardAim.of(remote), DashboardAim.onOpen(shown = DashboardAim.of(null), remote = remote))
  }

  @Test
  fun `opened again after Nouride came back to this phone, the screen shows this phone's`() {
    assertEquals(DashboardAim.of(null), DashboardAim.onOpen(shown = DashboardAim.of(remote), remote = null))
  }

  @Test
  fun `opened again for the Nouride on screen, it stays where the person was`() {
    assertNull(DashboardAim.onOpen(shown = DashboardAim.of(null), remote = null))
    assertNull(DashboardAim.onOpen(shown = DashboardAim.of(remote), remote = remote))
  }

  @Test
  fun `another address is another machine`() {
    val other = DashboardAim.onOpen(shown = DashboardAim.of(remote), remote = "https://other.example.com")
    assertEquals("https://other.example.com", other?.origin)
  }
}
