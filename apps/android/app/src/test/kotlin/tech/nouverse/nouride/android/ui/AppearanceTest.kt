package tech.nouverse.nouride.android.ui

import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceTest {
  private val phoneDark = Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_YES
  private val phoneLight = Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_NO

  @Test
  fun `Light and Dark set the night mode, and keep what else the phone says`() {
    assertEquals(phoneLight, Appearances.uiMode(phoneDark, Appearance.LIGHT))
    assertEquals(phoneDark, Appearances.uiMode(phoneLight, Appearance.DARK))
    assertEquals(phoneLight, Appearances.uiMode(phoneLight, Appearance.LIGHT))
  }

  @Test
  fun `Same as phone leaves the phone's own setting`() {
    assertEquals(phoneDark, Appearances.uiMode(phoneDark, Appearance.PHONE))
    assertEquals(phoneLight, Appearances.uiMode(phoneLight, Appearance.PHONE))
  }
}
