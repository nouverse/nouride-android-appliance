package tech.nouverse.nouride.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.nouverse.nouride.android.server.SetupProgress
import tech.nouverse.nouride.android.server.Step
import java.io.File
import java.nio.file.Files

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
  fun `a gateway port of its own goes into Nouride's config after the installer, and only for the debug build`() {
    val script = Nouride.installScript(18266)
    // After the installer, which makes /srv/nouride/.nouride, the account, and its own config.toml.
    assertTrue(script.indexOf(Nouride.gatewayConfig(18266)) > script.indexOf(Nouride.INSTALL_URL))
    // Nouride's own default needs no edit: a release's first start is as on every other install.
    assertFalse(Nouride.installScript(Nouride.DEFAULT_GATEWAY_PORT).contains("config.toml"))
  }

  /** Runs the edit with the host's shell on [before] (null: no file yet), and returns the file after it. */
  private fun edited(before: String?): String {
    val home = Files.createTempDirectory("nouride-home").toFile()
    val config = File(home, "config.toml")
    if (before != null) config.writeText(before)
    val run = ProcessBuilder("sh", "-c", Nouride.gatewayConfig(18266, config.path, home.path)).redirectErrorStream(true).start()
    val output = run.inputStream.bufferedReader().readText()
    assertEquals(output, 0, run.waitFor())
    return config.readText().also { home.deleteRecursively() }
  }

  @Test
  fun `the port goes under the installer's own nougate section`() {
    // What the Router edition's installer writes: the debug build lost to the release on 18256 with this file.
    assertEquals("[nougate]\nport = 18266\nin_process = true\n", edited("[nougate]\nin_process = true\n"))
  }

  @Test
  fun `no file is made, and a file without the section gets it`() {
    assertEquals("[nougate]\nport = 18266\n", edited(null))
    assertEquals("[daemon]\nlog = \"info\"\n\n[nougate]\nport = 18266\n", edited("[daemon]\nlog = \"info\"\n"))
  }

  @Test
  fun `a port already set is kept, and a port in another section is not the gateway's`() {
    assertEquals("[nougate]\nport = 18300\n", edited("[nougate]\nport = 18300\n"))
    assertEquals(
      "[dashboard]\nport = 18264\n[nougate]\nport = 18266\nin_process = true\n",
      edited("[dashboard]\nport = 18264\n[nougate]\nin_process = true\n"),
    )
  }
}
