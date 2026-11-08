package tech.nouverse.nouride.android.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DownloadedTest {
  private val dir: File = Files.createTempDirectory("updates").toFile()

  @Test
  fun `an APK downloaded and checked before is used again, not downloaded twice`() {
    val apk = File(dir, "nouride-home-0.1.2.apk").apply { writeText("the release") }
    val expected = Downloaded.sha256(apk)
    // An install cancelled or interrupted, then Update again: the same file, untouched.
    assertEquals(apk, Downloaded.keep(dir, "nouride-home-0.1.2.apk", expected.uppercase()))
    assertEquals("the release", apk.readText())
  }

  @Test
  fun `a file that no longer has the release's sha256, or another release's, is not kept`() {
    val apk = File(dir, "nouride-home-0.1.2.apk").apply { writeText("cut off half") }
    val older = File(dir, "nouride-home-0.1.1.apk").apply { writeText("an older release") }
    assertNull(Downloaded.keep(dir, "nouride-home-0.1.2.apk", "a".repeat(64)))
    assertFalse(apk.exists())
    assertFalse(older.exists())
    assertNull(Downloaded.keep(dir, "nouride-home-0.1.2.apk", "a".repeat(64)))
  }
}
