package tech.nouverse.nouride.android.boot

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootReceiverTest {
  @Test
  fun `Nouride starts again after a boot and after an update of the app, if it was running`() {
    assertTrue(BootReceiver.startsAgain(Intent.ACTION_BOOT_COMPLETED, wasRunning = true))
    // Android stops the app to replace it: an update must not leave the agents off until the app is opened.
    assertTrue(BootReceiver.startsAgain(Intent.ACTION_MY_PACKAGE_REPLACED, wasRunning = true))
  }

  @Test
  fun `never while Nouride lives on another machine`() {
    // This phone runs no server then: starting one would be a Nouride nobody asked for.
    assertFalse(BootReceiver.startsAgain(Intent.ACTION_BOOT_COMPLETED, wasRunning = true, remote = true))
    assertFalse(BootReceiver.startsAgain(Intent.ACTION_MY_PACKAGE_REPLACED, wasRunning = true, remote = true))
  }

  @Test
  fun `never when it was off, and never for another broadcast`() {
    assertFalse(BootReceiver.startsAgain(Intent.ACTION_MY_PACKAGE_REPLACED, wasRunning = false))
    assertFalse(BootReceiver.startsAgain(Intent.ACTION_BOOT_COMPLETED, wasRunning = false))
    assertFalse(BootReceiver.startsAgain(Intent.ACTION_PACKAGE_REPLACED, wasRunning = true))
    assertFalse(BootReceiver.startsAgain(null, wasRunning = true))
  }
}
