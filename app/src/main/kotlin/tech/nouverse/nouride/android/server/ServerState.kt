package tech.nouverse.nouride.android.server

import android.os.Handler
import android.os.Looper

/** What the service is doing, for the status screen and the notification. Same process only. */
object ServerState {
  enum class Phase { STOPPED, EXTRACTING, INSTALLING, STARTING, RUNNING, RESTARTING, STOPPING, FAILED }

  @Volatile
  var phase: Phase = Phase.STOPPED
    private set

  @Volatile
  var detail: String = ""
    private set

  @Volatile
  var health: Health? = null
    private set

  @Volatile
  var healthAt: Long = 0
    private set

  private val main = Handler(Looper.getMainLooper())
  private val listeners = mutableSetOf<() -> Unit>()

  fun set(phase: Phase, detail: String = "") {
    this.phase = phase
    this.detail = detail
    notifyListeners()
  }

  fun setHealth(health: Health, at: Long) {
    this.health = health
    this.healthAt = at
    notifyListeners()
  }

  fun listen(listener: () -> Unit) = main.post { listeners += listener }

  fun unlisten(listener: () -> Unit) = main.post { listeners -= listener }

  private fun notifyListeners() = main.post { listeners.toList().forEach { it() } }
}
