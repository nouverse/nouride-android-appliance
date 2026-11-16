package tech.nouverse.nouride.android.power

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerSettingsTest {
  @Test
  fun `Xiaomi's phones are told about Autostart, other phones are not`() {
    // The owner's Redmi Note 12 reports Xiaomi as its maker and Redmi as its brand.
    assertTrue(PowerSettings.asksForAutostart("Xiaomi", "Redmi"))
    assertTrue(PowerSettings.asksForAutostart("Xiaomi", "POCO"))
    assertTrue(PowerSettings.asksForAutostart("xiaomi", "xiaomi"))
    // Made by Xiaomi under a brand name of its own: the maker decides it.
    assertTrue(PowerSettings.asksForAutostart("Xiaomi", "Mi"))
    assertFalse(PowerSettings.asksForAutostart("samsung", "samsung"))
    assertFalse(PowerSettings.asksForAutostart("Google", "google"))
  }

  @Test
  fun `Open settings goes to Xiaomi's Autostart list, and to the app's page only on a phone without one`() {
    // HyperOS 2's page for the app has no Autostart switch; the list is where it is.
    assertEquals("miui.intent.action.OP_AUTO_START", PowerSettings.autostartScreen(opens = true))
    assertEquals("android.settings.APPLICATION_DETAILS_SETTINGS", PowerSettings.autostartScreen(opens = false))
  }
}
