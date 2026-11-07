package tech.nouverse.nouride.android

import org.junit.Assert.assertFalse
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
  fun `Nouride is told the app restarts it, so the dashboard's Restart and updates are not refused`() {
    val command = Nouride.START.last()
    // NOURIDE_SUPERVISOR_MODE, set for the daemon itself: after cd, on the same line as exec.
    assertTrue(command, command.contains("&& NOURIDE_SUPERVISOR_MODE=nouride-home exec ./nouride start"))
  }

  @Test
  fun `the setup script marks its steps in order, each before its work`() {
    val at = { step: Step -> script.indexOf(SetupProgress.marker(step)) }
    assertTrue(at(Step.PACKAGES) in 0 until script.indexOf("apt-get update"))
    assertTrue(at(Step.TOOLS) in script.indexOf("apt-get update") until script.indexOf("apt-get install"))
    assertTrue(at(Step.NOURIDE) in script.indexOf("apt-get install") until script.indexOf(Nouride.INSTALL_URL))
  }

  @Test
  fun `a gateway port of its own goes into Nouride's config before the wizard, for the account Nouride runs as`() {
    val line = Nouride.gatewayConfig(18266)
    assertTrue(line, line.startsWith("[ -e /srv/nouride/.nouride/config.toml ] ||"))
    assertTrue(line, line.contains("printf '[nougate]\\nport = %s\\n' 18266 > /srv/nouride/.nouride/config.toml"))
    assertTrue(line, line.contains("chown --reference=/srv/nouride/.nouride /srv/nouride/.nouride/config.toml"))
    // After the installer, which makes /srv/nouride/.nouride and the account.
    val script = Nouride.installScript(18266)
    assertTrue(script.indexOf(line) > script.indexOf(Nouride.INSTALL_URL))
    // Nouride's own default needs no file: a release's first start is as on every other install.
    assertFalse(Nouride.installScript(Nouride.DEFAULT_GATEWAY_PORT).contains("config.toml"))
  }
}
