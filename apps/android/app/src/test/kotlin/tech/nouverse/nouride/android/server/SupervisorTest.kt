package tech.nouverse.nouride.android.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class SupervisorTest {
  /** A process that ends when told to. */
  private class Fake : Process() {
    val end = CountDownLatch(1)
    override fun getOutputStream(): OutputStream = ByteArrayOutputStream()
    override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))
    override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))
    override fun waitFor(): Int { end.await(); return 0 }
    override fun exitValue(): Int = if (end.count == 0L) 0 else throw IllegalThreadStateException()
    override fun destroy() = end.countDown()
  }

  @Test
  fun `an exit the app asked for starts the daemon again at once, and is not counted against it`() {
    val started = LinkedBlockingQueue<Fake>()
    val delays = LinkedBlockingQueue<Long>()
    // A long first backoff: a restart counted as a stop on its own would wait ten seconds.
    val supervisor = Supervisor(
      start = { Fake() }, backoff = Backoff(baseMs = 10_000), clock = System::currentTimeMillis,
      onStarted = { started.put(it as Fake) }, onExited = { _, restartInMs -> restartInMs?.let(delays::put) },
    )
    val runner = thread { supervisor.run() }
    val first = started.poll(2, TimeUnit.SECONDS)!!
    supervisor.restartNext()
    first.destroy()
    assertTrue("started again at once", started.poll(2, TimeUnit.SECONDS) != null)
    assertEquals(0L, delays.poll(1, TimeUnit.SECONDS))
    supervisor.stop()
    started.forEach { it.destroy() }
    runner.join(2_000)
  }
}
