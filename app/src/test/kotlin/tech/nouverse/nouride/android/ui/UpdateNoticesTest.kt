package tech.nouverse.nouride.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.nouverse.nouride.android.R

class UpdateNoticesTest {
  @Test
  fun `the notification says a new Nouride as it says a new app`() {
    assertEquals(R.string.notification_nouride_update, UpdateNotices.notification(app = false, nouride = true))
    assertEquals(R.string.notification_update, UpdateNotices.notification(app = true, nouride = false))
    assertEquals(R.string.notification_updates, UpdateNotices.notification(app = true, nouride = true))
    assertEquals(R.string.notification_tap, UpdateNotices.notification(app = false, nouride = false))
  }

  @Test
  fun `More's Versions line names what waits, and the versions when nothing does`() {
    assertEquals(R.string.versions_row_nouride_update, UpdateNotices.versionsRow(app = false, nouride = true))
    assertEquals(R.string.versions_row_update, UpdateNotices.versionsRow(app = true, nouride = false))
    assertEquals(R.string.versions_row_updates, UpdateNotices.versionsRow(app = true, nouride = true))
    assertNull(UpdateNotices.versionsRow(app = false, nouride = false))
  }

  @Test
  fun `More carries a dot for an update waiting there, not only for the battery`() {
    assertTrue(UpdateNotices.moreDot(mayStop = false, app = false, nouride = true))
    assertTrue(UpdateNotices.moreDot(mayStop = false, app = true, nouride = false))
    assertTrue(UpdateNotices.moreDot(mayStop = true, app = false, nouride = false))
    assertFalse(UpdateNotices.moreDot(mayStop = false, app = false, nouride = false))
  }
}
