package tech.nouverse.nouride.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.nouverse.nouride.android.R
import tech.nouverse.nouride.android.backup.BackupStatus
import tech.nouverse.nouride.android.backup.BackupStatus.Step
import tech.nouverse.nouride.android.server.Where

class BackupLooksTest {
  @Test
  fun `only this phone's own Nouride, once installed`() {
    assertTrue(BackupLooks.shown(Where.PHONE, installed = true))
    assertTrue(BackupLooks.shown(null, installed = true))
    assertFalse(BackupLooks.shown(Where.REMOTE, installed = true))
    assertFalse(BackupLooks.shown(Where.PHONE, installed = false))
  }

  @Test
  fun `every step but the first says where it is, or what happened`() {
    for (step in Step.values().filter { it != Step.IDLE }) assertNotEquals(step.name, 0, BackupLooks.result(step))
    assertEquals(0, BackupLooks.result(Step.IDLE))
  }

  @Test
  fun `nothing to press until it is counted, nor while a zip is made`() {
    assertFalse(BackupLooks.canStart(BackupStatus()))
    assertTrue(BackupLooks.canStart(BackupStatus(size = 10)))
    for (step in listOf(Step.SNAPSHOT, Step.SAVING)) assertFalse(BackupLooks.canStart(BackupStatus(size = 10, step = step)))
    for (step in listOf(Step.SAVED, Step.NO_ROOM, Step.NO_SNAPSHOT, Step.FAILED)) assertTrue(BackupLooks.canStart(BackupStatus(size = 10, step = step)))
  }

  @Test
  fun `the bar moves only while it works, with a number only while the zip is written`() {
    assertEquals(setOf(Step.SNAPSHOT, Step.SAVING), Step.values().filter(BackupLooks::bar).toSet())
    assertNull(BackupLooks.percent(BackupStatus(step = Step.SNAPSHOT)))
    assertEquals(25, BackupLooks.percent(BackupStatus(step = Step.SAVING, done = 25, total = 100)))
    assertEquals(100, BackupLooks.percent(BackupStatus(step = Step.SAVING, done = 120, total = 100)))
    assertEquals(0, BackupLooks.percent(BackupStatus(step = Step.SAVING, done = 5, total = 0)))
  }

  @Test
  fun `the secrets are said as they are, sealed in or out`() {
    assertEquals(R.string.backup_sealed, BackupLooks.secrets(BackupStatus(sealed = true)))
    assertEquals(R.string.backup_unsealed, BackupLooks.secrets(BackupStatus(sealed = false)))
    assertEquals(R.string.backup_counting, BackupLooks.size(BackupStatus()))
    assertEquals(R.string.backup_size, BackupLooks.size(BackupStatus(size = 1)))
  }
}
