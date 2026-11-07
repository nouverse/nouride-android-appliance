package tech.nouverse.nouride.android

import org.junit.Assert.assertTrue
import org.junit.Test
import tech.nouverse.nouride.android.server.SetupProgress
import tech.nouverse.nouride.android.server.Step

class NourideTest {
  private val script = Nouride.INSTALL.last()

  @Test
  fun `the phone gets the Router edition, on Nouride's own port, open to the Wi-Fi`() {
    val installer = script.lines().single { it.contains(Nouride.INSTALL_URL) }
    assertTrue(installer, installer.contains("--variant router"))
    assertTrue(installer, installer.contains("--port ${Nouride.PORT}"))
    assertTrue(installer, installer.contains("--host 0.0.0.0"))
  }

  @Test
  fun `the setup script marks its steps in order, each before its work`() {
    val at = { step: Step -> script.indexOf(SetupProgress.marker(step)) }
    assertTrue(at(Step.PACKAGES) in 0 until script.indexOf("apt-get update"))
    assertTrue(at(Step.TOOLS) in script.indexOf("apt-get update") until script.indexOf("apt-get install"))
    assertTrue(at(Step.NOURIDE) in script.indexOf("apt-get install") until script.indexOf(Nouride.INSTALL_URL))
  }
}
