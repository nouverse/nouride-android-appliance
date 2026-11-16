package tech.nouverse.nouride.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * The Connect tab's QR code, module by module, against segno 1.6.6 (an independent encoder) with error correction M in
 * byte mode: the sha256 of its matrix, rows of 1 and 0 joined by newlines, without the quiet zone. Generated with
 * `segno.make(text, error="m", mode="byte", boost_error=False, micro=False[, version=, mask=])`, with its padding set
 * to ISO/IEC 18004:2015 7.4.10 (segno adds a zero byte when the stream already ends on a byte boundary, which in byte
 * mode it always does): every mask once, the mask its penalty picks, version 7 and up (version information), and
 * version 10 (a 16-bit length).
 */
class QrCodeTest {
  private class Case(val text: String, val version: Int, val mask: Int, val forced: Boolean, val sha256: String)

  private val cases = listOf(
    Case("HELLO", version = 1, mask = 2, forced = false, sha256 = "1fff7fd781f0f59edd796e0f79b20d68de6ecef41b47571460c4bbfbac1afe97"),
    Case("http://192.168.1.23:18254", version = 2, mask = 6, forced = false, sha256 = "61cd83e0ee2bf535a149885c6a3a18f711058ff9ad401e3d4da12d5ae6866d22"),
    Case("http://10.18.1.99:18264", version = 2, mask = 4, forced = false, sha256 = "dc138a0602a5855a255ac756039fbeba7dbc07789a0cc195972600e6c02f3cfd"),
    Case("http://192.168.1.23:18254", version = 2, mask = 0, forced = true, sha256 = "d33cfbfc2dc8db605ac7da3acc44b58ad7100c3b360a05c96c10585e5b0e697d"),
    Case("http://192.168.1.23:18254", version = 2, mask = 1, forced = true, sha256 = "777d68e55b414c2e11f4ab8e9afac362c9c57f926545a8c935995f47f61a8493"),
    Case("http://192.168.1.23:18254", version = 2, mask = 2, forced = true, sha256 = "50a6187b4c7f036241fc4523f07b13917cb223ecc5d585279c5a9baf87d76949"),
    Case("http://192.168.1.23:18254", version = 2, mask = 3, forced = true, sha256 = "1300e8a8fed1810f0456f0d95de9ba92dbb6969c60bf5f5df2531bf1a0a77696"),
    Case("http://192.168.1.23:18254", version = 2, mask = 4, forced = true, sha256 = "069de27540534e41642ab509534cbf7e1e283e83834928532e574dab4416c42f"),
    Case("http://192.168.1.23:18254", version = 2, mask = 5, forced = true, sha256 = "c72d46689d1e91479180b8672115913266cc8626afc54af10a374ec9962349d8"),
    Case("http://192.168.1.23:18254", version = 2, mask = 6, forced = true, sha256 = "61cd83e0ee2bf535a149885c6a3a18f711058ff9ad401e3d4da12d5ae6866d22"),
    Case("http://192.168.1.23:18254", version = 2, mask = 7, forced = true, sha256 = "f5ce30a6f5130fde8d2e0debb2dc1c01d099d01e02d745c67592962934b367a4"),
    Case("x".repeat(110), version = 7, mask = 0, forced = false, sha256 = "1fc0b5f1273847bfdc50100f139ec46f6d584078234406409dde9e20a6410ae0"),
    Case("Nouride Home ".repeat(9), version = 7, mask = 3, forced = true, sha256 = "1910eff5e3913b5de0c66edd0a6e464050c8b40037d98e46d17ac01a8601df13"),
    Case("https://github.com/nouverse/nouride-android-appliance/releases/download/v0.1.2/nouride-home-0.1.2.apk?" + "a".repeat(90), version = 10, mask = 2, forced = false, sha256 = "981403e3d012764a432d466ce2135cb7713f8b4804143a1d48c05bca9f0301ed"),
  )

  private fun digest(modules: Array<BooleanArray>): String =
    MessageDigest.getInstance("SHA-256")
      .digest(modules.joinToString("\n") { row -> row.joinToString("") { if (it) "1" else "0" } }.toByteArray())
      .joinToString("") { "%02x".format(it) }

  @Test
  fun `every module is the reference encoder's, for every mask and version`() {
    for (case in cases) {
      val symbol = QrCode.encode(case.text.toByteArray(), if (case.forced) case.version else null, if (case.forced) case.mask else null)
      assertEquals("version for ${case.text.take(24)}", case.version, symbol.version)
      assertEquals("mask for ${case.text.take(24)}", case.mask, symbol.mask)
      assertEquals("modules for ${case.text.take(24)} (mask ${case.mask})", case.sha256, digest(symbol.modules))
    }
  }

  @Test
  fun `an address on the phone fits a small code, and too much text is refused rather than cut`() {
    val modules = QrCode.encode("http://192.168.100.200:18254")
    assertTrue(modules.size <= 29)
    assertTrue(runCatching { QrCode.encode("a".repeat(300)) }.isFailure)
  }
}
