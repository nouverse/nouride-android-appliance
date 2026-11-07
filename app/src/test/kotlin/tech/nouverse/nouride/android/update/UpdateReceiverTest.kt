package tech.nouverse.nouride.android.update

import android.content.pm.PackageInstaller
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.nouverse.nouride.android.server.ServerState.Phase

class UpdateReceiverTest {
  @Test
  fun `the new version starts Nouride again after its update went in, if Nouride was running`() {
    // HyperOS kept MY_PACKAGE_REPLACED from the app: Nouride stayed off for 50 minutes after an update on the phone.
    assertTrue(UpdateReceiver.startsAgain(PackageInstaller.STATUS_SUCCESS, wasRunning = true))
  }

  @Test
  fun `never when Nouride was off, and never after an update that did not go in`() {
    assertFalse(UpdateReceiver.startsAgain(PackageInstaller.STATUS_SUCCESS, wasRunning = false))
    assertFalse(UpdateReceiver.startsAgain(PackageInstaller.STATUS_FAILURE_ABORTED, wasRunning = true))
    assertFalse(UpdateReceiver.startsAgain(PackageInstaller.STATUS_FAILURE, wasRunning = true))
    assertFalse(UpdateReceiver.startsAgain(PackageInstaller.STATUS_PENDING_USER_ACTION, wasRunning = true))
  }

  @Test
  fun `an update waits from the press of Start until Nouride is set up`() {
    val notYet = { false }
    val done = { true }
    // Pressed, and the service has not reported yet: the big card came here once, over the first setup.
    assertTrue(Updates.waitsFor(Phase.STOPPED, startPressed = true, installed = notYet))
    assertTrue(Updates.waitsFor(Phase.EXTRACTING, startPressed = false, installed = notYet))
    assertTrue(Updates.waitsFor(Phase.INSTALLING, startPressed = false, installed = notYet))
    // Set up: every later start is quick and resumes after an update.
    assertFalse(Updates.waitsFor(Phase.STARTING, startPressed = true, installed = done))
    assertFalse(Updates.waitsFor(Phase.RUNNING, startPressed = true, installed = done))
    // Nothing started yet: nothing to cut off, so a newer version may go in first.
    assertFalse(Updates.waitsFor(Phase.STOPPED, startPressed = false, installed = notYet))
    // A setup that failed may be what the update fixes.
    assertFalse(Updates.waitsFor(Phase.FAILED, startPressed = true, installed = notYet))
  }
}
