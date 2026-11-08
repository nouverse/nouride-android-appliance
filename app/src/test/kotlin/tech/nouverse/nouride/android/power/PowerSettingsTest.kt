package tech.nouverse.nouride.android.power

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerSettingsTest {
  @Test
  fun `Xiaomi's phones are told about Autostart, other phones are not`() {
    // The owner's Redmi 12 reports Xiaomi as its maker and Redmi as its brand.
    assertTrue(PowerSettings.asksForAutostart("Xiaomi", "Redmi"))
    assertTrue(PowerSettings.asksForAutostart("Xiaomi", "POCO"))
    assertTrue(PowerSettings.asksForAutostart("xiaomi", "xiaomi"))
    // Made by Xiaomi under a brand name of its own: the maker decides it.
    assertTrue(PowerSettings.asksForAutostart("Xiaomi", "Mi"))
    assertFalse(PowerSettings.asksForAutostart("samsung", "samsung"))
    assertFalse(PowerSettings.asksForAutostart("Google", "google"))
  }
}
