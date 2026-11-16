package tech.nouverse.nouride.android.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FontsTest {
  @Test
  fun `big bold words are a heading, small bold ones a label, the rest body`() {
    assertEquals(Type.HEADING, Fonts.of(24f, bold = true))
    assertEquals(Type.HEADING, Fonts.of(17f, bold = true))
    assertEquals(Type.STRONG, Fonts.of(15f, bold = true))
    assertEquals(Type.STRONG, Fonts.of(12f, bold = true))
    assertEquals(Type.BODY, Fonts.of(24f, bold = false))
    assertEquals(Type.BODY, Fonts.of(15f, bold = false))
  }

  @Test
  fun `every face is in the app, with its licence`() {
    // Gradle runs unit tests from the module's folder; an IDE may run them from the project's.
    val folder = listOf(File("src/main/assets/fonts"), File("app/src/main/assets/fonts")).first { it.isDirectory }
    for (type in Type.values()) {
      val font = File(folder, Fonts.file(type))
      assertTrue("$type: ${font.name}", font.isFile && font.length() > 1000)
      // A TrueType file starts with 0x00010000; a WOFF2 the dashboard serves would not load on Android.
      assertEquals("$type is TrueType", listOf(0, 1, 0, 0), font.readBytes().take(4).map { it.toInt() })
    }
    for (licence in listOf("OFL-Fredoka.txt", "OFL-Manrope.txt", "OFL-DMMono.txt")) {
      assertTrue(licence, File(folder, licence).readText().contains("SIL Open Font License"))
    }
  }
}
