package tech.nouverse.nouride.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.nouverse.nouride.android.server.ServerState.Phase

class TabsTest {
  private fun look(phase: Phase, answering: Boolean = true) = StatusLook.of(phase, answering)

  @Test
  fun `the bar steps aside only while the first start sets Nouride up`() {
    assertFalse(Tabs.barShown(look(Phase.EXTRACTING), firstStart = true))
    assertFalse(Tabs.barShown(look(Phase.INSTALLING), firstStart = true))
    assertFalse(Tabs.barShown(look(Phase.STARTING), firstStart = true))
    // Every later start is seconds long, and the person may want More or Connect meanwhile.
    assertTrue(Tabs.barShown(look(Phase.STARTING), firstStart = false))
    assertTrue(Tabs.barShown(look(Phase.RESTARTING), firstStart = false))
    assertTrue(Tabs.barShown(look(Phase.RUNNING), firstStart = false))
    // Before the first Start, and after a failed one: More is where the app's own update is.
    assertTrue(Tabs.barShown(look(Phase.STOPPED), firstStart = false))
    assertTrue(Tabs.barShown(look(Phase.FAILED), firstStart = true))
  }

  @Test
  fun `back from Connect or More goes Home, and from Home it leaves`() {
    assertEquals(Tab.HOME, Tabs.back(Tab.CONNECT))
    assertEquals(Tab.HOME, Tabs.back(Tab.MORE))
    assertNull(Tabs.back(Tab.HOME))
  }

  @Test
  fun `Connect offers the address only when Nouride answers, others may open it, and the phone is on Wi-Fi`() {
    assertEquals(Reach.READY, Tabs.reach(look(Phase.RUNNING), "192.168.1.23", openToOthers = true))
    assertEquals(Reach.NO_WIFI, Tabs.reach(look(Phase.RUNNING), null, openToOthers = true))
    // Kept to this phone by the switch: no address, whatever the Wi-Fi.
    assertEquals(Reach.THIS_PHONE, Tabs.reach(look(Phase.RUNNING), "192.168.1.23", openToOthers = false))
    assertEquals(Reach.OFF, Tabs.reach(look(Phase.RUNNING, answering = false), "192.168.1.23", openToOthers = true))
    assertEquals(Reach.OFF, Tabs.reach(look(Phase.STOPPED), "192.168.1.23", openToOthers = false))
    assertEquals(Reach.OFF, Tabs.reach(look(Phase.INSTALLING), "192.168.1.23", openToOthers = true))
  }
}
