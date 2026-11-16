package tech.nouverse.nouride.android.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.nouverse.nouride.android.R
import tech.nouverse.nouride.android.server.Health
import tech.nouverse.nouride.android.server.RemoteHost
import tech.nouverse.nouride.android.server.ServerState.Phase
import tech.nouverse.nouride.android.server.Where

class AddressFormTest {
  @Test
  fun `each refusal has its own sentence, and an address has none`() {
    assertEquals(R.string.where_empty, AddressForm.refusal(RemoteHost.read("  ")))
    assertEquals(R.string.where_plain_http, AddressForm.refusal(RemoteHost.read("http://nouride.example.com")))
    assertEquals(R.string.where_not_an_address, AddressForm.refusal(RemoteHost.read("nouride example.com")))
    assertNull(AddressForm.refusal(RemoteHost.read("nouride.example.com")))
  }

  @Test
  fun `any answer is taken, and no answer at all keeps the screen open`() {
    assertTrue(AddressForm.answered(Health.parse(200, """{"status":"ok"}""")))
    // A page in front of Nouride, or a tunnel whose computer is off: Home says how that computer is.
    assertTrue(AddressForm.answered(Health.parse(403, "Forbidden")))
    assertTrue(AddressForm.answered(Health.parse(530, "<html>error code: 1033</html>")))
    assertFalse(AddressForm.answered(Health.unreachable("java.net.UnknownHostException: nouride.example.com")))
  }

  @Test
  fun `the screen warns that this phone's Nouride turns off only while it is on`() {
    assertTrue(AddressForm.stopsHere(Where.PHONE, Phase.RUNNING))
    assertTrue(AddressForm.stopsHere(Where.PHONE, Phase.STARTING))
    assertFalse(AddressForm.stopsHere(Where.PHONE, Phase.STOPPED))
    assertFalse(AddressForm.stopsHere(Where.PHONE, Phase.FAILED))
    // The first screen: nothing runs here yet.
    assertFalse(AddressForm.stopsHere(null, Phase.STOPPED))
    // Change address: Nouride already lives on the other computer, even while this phone's is still winding down.
    assertFalse(AddressForm.stopsHere(Where.REMOTE, Phase.STOPPED))
    assertFalse(AddressForm.stopsHere(Where.REMOTE, Phase.RUNNING))
  }

  /** A screen of its own, as Versions and Technical Logs are: declared, with the keyboard up and the screen above it. */
  @Test
  fun `the address screen is declared, with the keyboard up`() {
    val manifest = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml")).first { it.exists() }.readText()
    val entry = Regex("""<activity\s[^>]*android:name="\.ui\.AddressActivity"[^>]*>""").find(manifest)?.value
    assertTrue(manifest, entry != null)
    assertTrue(entry!!, entry.contains("""android:exported="false""""))
    assertTrue(entry, entry.contains("stateVisible|adjustResize"))
  }
}
