package tech.nouverse.nouride.android.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Nouride's update answers, in the shapes `packages/wire/src/update.ts` gives them in Nouride. */
class NourideUpdateTest {
  private val install = """{"version":"0.8.24","edition":"router","installDir":"/srv/nouride","supervisor":"external"}"""
  private val idle = """{"status":"idle","received":0,"total":null,"tag":null,"artifact":null,"error":null}"""

  @Test
  fun `a newer release is the version on offer`() {
    val status = NourideUpdate.status(
      """{"supported":true,"install":$install,"download":$idle,
        "check":{"current":"0.8.24","latest":"v0.8.25","newer":true,"publishedAt":null,"notesUrl":null,
        "plan":{"can":true,"from":"0.8.24","to":"0.8.25"}}}""",
    )!!
    assertEquals("0.8.24", status.current)
    assertEquals("0.8.25", status.ready)
    assertTrue(status.canUpdate)
    assertFalse(status.checkFailed)
    assertEquals(NourideDownload.Status.IDLE, status.download.status)
  }

  @Test
  fun `the newest release offers nothing, even when the check names it as latest`() {
    val status = NourideUpdate.status(
      """{"supported":true,"install":$install,"download":$idle,
        "check":{"current":"0.8.25","latest":"0.8.25","newer":false,"publishedAt":null,"notesUrl":null}}""",
    )!!
    assertEquals("0.8.25", status.current)
    assertNull(status.ready)
    assertFalse(status.checkFailed)
  }

  @Test
  fun `a release channel that did not answer is a failed check, never up to date`() {
    // Nouride answers 200 with the error as data, and no check (`routes/update.ts`).
    val status = NourideUpdate.status(
      """{"supported":true,"install":$install,"download":$idle,"error":"fetch failed","plain":"Nouride could not check"}""",
    )!!
    assertTrue(status.checkFailed)
    assertNull(status.ready)
    // The version is still known, from the install.
    assertEquals("0.8.24", status.current)
  }

  @Test
  fun `a build that cannot update itself, or a plan that cannot, is said to be one`() {
    val unsupported = NourideUpdate.status("""{"supported":false,"install":$install,"download":$idle}""")!!
    assertFalse(unsupported.canUpdate)
    val cannot = NourideUpdate.status(
      """{"supported":true,"install":$install,"download":$idle,
        "check":{"current":"0.8.24","latest":"0.8.25","newer":true,"plan":{"can":false,"from":"0.8.24","to":"0.8.25","reason":"x"}}}""",
    )!!
    assertFalse(cannot.canUpdate)
    assertEquals("0.8.25", cannot.ready)
  }

  @Test
  fun `a download's percent comes from its bytes, and is 0 while its size is unknown`() {
    val going = NourideUpdate.download("""{"ok":true,"download":{"status":"downloading","received":28563088,"total":57126176}}""")!!
    assertEquals(NourideDownload.Status.DOWNLOADING, going.status)
    assertEquals(50, going.percent)
    val unknown = NourideUpdate.download("""{"download":{"status":"downloading","received":1000,"total":null}}""")!!
    assertNull(unknown.total)
    assertEquals(0, unknown.percent)
    assertEquals(NourideDownload.Status.READY, NourideUpdate.download("""{"download":{"status":"ready","received":5,"total":5}}""")!!.status)
    assertEquals(NourideDownload.Status.FAILED, NourideUpdate.download("""{"download":{"status":"failed","received":0,"total":null}}""")!!.status)
    assertNull(NourideUpdate.download("""{"ok":true}"""))
    assertNull(NourideUpdate.download("not json"))
  }

  @Test
  fun `the install's answer says when Nouride restarts`() {
    assertEquals(Restart.NOW, NourideUpdate.restart("""{"ok":true,"to":"0.8.25","restart":"now"}"""))
    assertEquals(Restart.IDLE, NourideUpdate.restart("""{"ok":true,"to":"0.8.25","restart":"idle"}"""))
    assertEquals(Restart.JOBS, NourideUpdate.restart("""{"ok":true,"to":"0.8.25","restart":"jobs"}"""))
    // An older Nouride that does not say: it restarts at once.
    assertEquals(Restart.NOW, NourideUpdate.restart("""{"ok":true,"to":"0.8.25"}"""))
  }

  @Test
  fun `an answer that is not one is null, not up to date`() {
    assertNull(NourideUpdate.status("<html>"))
  }

  @Test
  fun `the token is the CLI's, in the install's data folder under the rootfs`() {
    // `cli/client.ts` in Nouride: `<dataDir>/.control-token`, dataDir `./.nouride/data` from the install, /srv/nouride.
    assertEquals("srv/nouride/.nouride/data/.control-token", NourideUpdate.TOKEN_PATH)
  }
}
