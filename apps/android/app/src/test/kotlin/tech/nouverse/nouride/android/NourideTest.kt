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

  @Test
  fun `the account is made by the setup script, after the packages and before the installer`() {
    val account = script.indexOf(Nouride.serviceAccount())
    assertTrue(account > script.indexOf(SetupProgress.marker(Step.NOURIDE)))
    assertTrue(account < script.indexOf(Nouride.INSTALL_URL))
  }

  /** The four files as Debian's rootfs has them, a system account at [taken] (a uid and a gid) if given. */
  private fun etc(vararg taken: Int): File {
    val etc = Files.createTempDirectory("nouride-etc").toFile()
    File(etc, "passwd").writeText(
      "root:x:0:0:root:/root:/bin/bash\n_apt:x:42:65534::/nonexistent:/usr/sbin/nologin\n" +
        "nobody:x:65534:65534:nobody:/nonexistent:/usr/sbin/nologin\n" +
        taken.joinToString("") { "svc$it:x:$it:$it::/nonexistent:/usr/sbin/nologin\n" },
    )
    File(etc, "group").writeText("root:x:0:\nusers:x:100:\nnogroup:x:65534:\n")
    File(etc, "shadow").writeText("root:*:20761:0:99999:7:::\n")
    File(etc, "gshadow").writeText("root:*::\n")
    return etc
  }

  private fun runAccount(etc: File) {
    val run = ProcessBuilder("sh", "-c", "set -e; " + Nouride.serviceAccount(etc.path)).redirectErrorStream(true).start()
    val output = run.inputStream.bufferedReader().readText()
    assertEquals(output, 0, run.waitFor())
  }

  private fun File.line(name: String) = File(this, name).readLines().filter { it.startsWith("nouride:") }

  @Test
  fun `a phone without the account gets it in all four files, as useradd --system makes it`() {
    val etc = etc()
    runAccount(etc)
    assertEquals(listOf("nouride:x:999:999::/srv/nouride/.nouride/home:/usr/sbin/nologin"), etc.line("passwd"))
    assertEquals(listOf("nouride:x:999:"), etc.line("group"))
    assertEquals(listOf("nouride:!::"), etc.line("gshadow"))
    // Locked, changed today, no aging: what shadow's useradd writes for a system account.
    val shadow = etc.line("shadow").single().split(":")
    assertEquals(listOf("nouride", "!"), shadow.take(2))
    assertEquals(System.currentTimeMillis() / 86_400_000, shadow[2].toLong())
    assertEquals(List(6) { "" }, shadow.drop(3))
    // What was there stays first, untouched.
    assertEquals("root:x:0:0:root:/root:/bin/bash", File(etc, "passwd").readLines().first())
    etc.deleteRecursively()
  }

  @Test
  fun `an id taken as a user's or as a group's is skipped`() {
    val etc = etc(999)
    File(etc, "group").appendText("svc998:x:998:\n")
    runAccount(etc)
    assertEquals(listOf("nouride:x:997:997::/srv/nouride/.nouride/home:/usr/sbin/nologin"), etc.line("passwd"))
    assertEquals(listOf("nouride:x:997:"), etc.line("group"))
    etc.deleteRecursively()
  }

  @Test
  fun `an account already there is left as it is, so running it again changes nothing`() {
    val etc = etc()
    runAccount(etc)
    val after = listOf("passwd", "group", "shadow", "gshadow").associateWith { File(etc, it).readText() }
    runAccount(etc)
    assertEquals(after, listOf("passwd", "group", "shadow", "gshadow").associateWith { File(etc, it).readText() })
    etc.deleteRecursively()
  }
}
