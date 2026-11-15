package tech.nouverse.nouride.android.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NourideNoticeTest {
  private fun status(ready: String?, canUpdate: Boolean = true, checkFailed: Boolean = false) =
    NourideStatus("0.8.38", ready, canUpdate, checkFailed, NourideDownload(NourideDownload.Status.IDLE, 0, null))

  @Test
  fun `a newer Nouride this install can put in is worth a notice`() {
    assertEquals("0.8.39", NourideNotice.noticeFor(status("0.8.39")))
  }

  @Test
  fun `none when it is up to date, or cannot update itself`() {
    assertNull(NourideNotice.noticeFor(status(null)))
    // Its card would offer an update that cannot happen.
    assertNull(NourideNotice.noticeFor(status("0.8.39", canUpdate = false)))
  }

  @Test
  fun `the notice goes once the Nouride running has that version or a newer one`() {
    assertEquals("0.8.39", NourideNotice.stillWaiting("0.8.39", "0.8.38"))
    assertNull(NourideNotice.stillWaiting("0.8.39", "0.8.39"))
    assertNull(NourideNotice.stillWaiting("0.8.39", "0.8.40"))
    // Nouride off: what was heard last stands until it answers again.
    assertEquals("0.8.39", NourideNotice.stillWaiting("0.8.39", null))
    assertNull(NourideNotice.stillWaiting(null, "0.8.38"))
  }
}
