package tech.nouverse.nouride.android.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import tech.nouverse.nouride.android.Layout
import tech.nouverse.nouride.android.Nouride
import tech.nouverse.nouride.android.install.AndroidFileOps
import tech.nouverse.nouride.android.install.AndroidIds
import tech.nouverse.nouride.android.install.RootfsExtractor
import tech.nouverse.nouride.android.install.RootfsSetup
import tech.nouverse.nouride.android.ui.StatusActivity
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Runs Nouride as a foreground service: unpacks Debian on the first start, runs the official
 * installer inside it once, then keeps `nouride start` running under proot, restarting it with a
 * backoff when it stops on its own. Holds a partial wake lock and a Wi-Fi lock while it runs, as
 * `termux-wake-lock` does, so the daemon keeps answering on the network with the screen off.
 */
class ServerService : Service() {
  private lateinit var layout: Layout
  private lateinit var runner: ProotRunner
  private var wakeLock: PowerManager.WakeLock? = null
  private var wifiLock: WifiManager.WifiLock? = null
  private var worker: Thread? = null
  private var poller: HealthPoller? = null

  @Volatile
  private var supervisor: Supervisor? = null

  @Volatile
  private var current: Process? = null

  @Volatile
  private var stopping = false

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    layout = Layout(this)
    val args = ProotArgs(layout.prootPaths(), exists = { File(it).exists() }, readable = ::readable)
    runner = ProotRunner(args, layout.prootTmp)
    if (Build.VERSION.SDK_INT >= 26) {
      getSystemService(NotificationManager::class.java)
        .createNotificationChannel(NotificationChannel(CHANNEL, "Server", NotificationManager.IMPORTANCE_LOW))
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    // Within seconds of startForegroundService(), whatever the intent asks.
    showNotification()
    if (intent?.action == ACTION_STOP) {
      stopServer()
      return START_NOT_STICKY
    }
    // A null intent is the system bringing the service back after it killed the app.
    startServer(if (intent == null) "restarted by the system" else "started")
    return START_STICKY
  }

  override fun onDestroy() {
    if (worker?.isAlive == true) event("service destroyed while the server ran")
    super.onDestroy()
  }

  private fun startServer(why: String) {
    if (worker?.isAlive == true) return
    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(PREF_RUN, true).apply()
    event("server $why")
    acquireLocks()
    stopping = false
    worker = thread(name = "nouride-server") { runServer() }
    poller = HealthPoller(Nouride.HEALTH_URL, HEALTH_INTERVAL_SECONDS, ::onHealth).also { it.start() }
  }

  private fun runServer() {
    try {
      if (!layout.rootfs.isDirectory) extractRootfs()
      prepareHostDirs()
      if (!layout.installed) installNouride()
      if (!stopping) superviseDaemon()
    } catch (e: Exception) {
      event("failed: $e")
      ServerState.set(ServerState.Phase.FAILED, e.message ?: e.toString())
      showNotification()
    }
  }

  private fun extractRootfs() {
    ServerState.set(ServerState.Phase.EXTRACTING, "unpacking Debian")
    showNotification()
    if (layout.rootfsPartial.exists()) deleteTree(layout.rootfsPartial)
    val began = SystemClock.elapsedRealtime()
    val count = assets.open(ROOTFS_ASSET).use { input ->
      RootfsExtractor(layout.rootfsPartial, AndroidFileOps).extract(input) { n ->
        ServerState.set(ServerState.Phase.EXTRACTING, "unpacking Debian: $n files")
      }
    }
    val ids = AndroidIds.parse(command("/system/bin/id"))
    if (ids == null) event("could not read the app's ids from /system/bin/id: no aid_ entries")
    RootfsSetup.apply(layout.rootfsPartial, ids, AndroidFileOps)
    if (!layout.rootfsPartial.renameTo(layout.rootfs)) throw IOException("cannot move the rootfs into place")
    event("rootfs unpacked: $count entries in ${(SystemClock.elapsedRealtime() - began) / 1000}s")
  }

  /** The fake /proc files are written at every start: they carry the uptime and boot time. */
  private fun prepareHostDirs() {
    layout.shm.mkdirs()
    Os.chmod(layout.shm.path, "1777".toInt(8))
    layout.sysdata.mkdirs()
    val uptime = SystemClock.elapsedRealtime() / 1000
    val bootTime = System.currentTimeMillis() / 1000 - uptime
    SysData.write(layout.sysdata, SysData.contents(SysData.cpuIds(File("/proc/cpuinfo").readText()), uptime, bootTime))
  }

  private fun installNouride() {
    ServerState.set(ServerState.Phase.INSTALLING, "running the Nouride installer")
    showNotification()
    event("installer started")
    val process = runner.start(Nouride.INSTALL, layout.installLog) { line ->
      ServerState.set(ServerState.Phase.INSTALLING, line.take(160))
    }
    current = process
    val code = process.waitFor()
    current = null
    event("installer exited with $code")
    if (stopping) return
    if (code != 0 || !layout.installed) throw IOException("the installer exited with $code; see install.log")
  }

  private fun superviseDaemon() {
    val supervisor = Supervisor(
      start = {
        runner.start(Nouride.START, layout.daemonLog) { line ->
          if (line.contains(Nouride.READY_LINE)) {
            event("daemon ready")
            ServerState.set(ServerState.Phase.RUNNING, "ready")
            showNotification()
          }
        }
      },
      backoff = Backoff(),
      clock = SystemClock::elapsedRealtime,
      onStarted = { process ->
        current = process
        event("daemon started")
        ServerState.set(ServerState.Phase.STARTING, "starting the daemon")
        showNotification()
      },
      onExited = { code, restartInMs ->
        current = null
        if (restartInMs == null) {
          event("daemon exited with $code")
        } else {
          event("daemon exited with $code, restarting in ${restartInMs / 1000}s")
          ServerState.set(ServerState.Phase.RESTARTING, "exited with $code, restarting in ${restartInMs / 1000}s")
          showNotification()
        }
      },
    )
    this.supervisor = supervisor
    supervisor.run()
  }

  private fun stopServer() {
    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(PREF_RUN, false).apply()
    if (worker?.isAlive != true) {
      finish()
      return
    }
    event("server stopping")
    stopping = true
    ServerState.set(ServerState.Phase.STOPPING)
    showNotification()
    supervisor?.stop()
    thread(name = "nouride-stop") {
      current?.let(::terminate)
      worker?.join(30_000)
      event("server stopped")
      finish()
    }
  }

  private fun finish() {
    poller?.stop()
    poller = null
    releaseLocks()
    ServerState.set(ServerState.Phase.STOPPED)
    @Suppress("DEPRECATION")
    stopForeground(true)
    stopSelf()
  }

  /**
   * SIGTERM to `./nouride start` lets the daemon shut down cleanly; proot ignores SIGTERM itself.
   * Then SIGQUIT to proot, which makes it kill everything it runs, and last SIGKILL.
   */
  private fun terminate(process: Process) {
    val entries = Proc.list()
    val proot = entries.firstOrNull {
      it.ppid == android.os.Process.myPid() && it.cmdline.firstOrNull()?.endsWith("libproot.so") == true
    }
    if (proot != null) {
      val daemon = Proc.descendants(entries, proot.pid).firstOrNull {
        it.cmdline.size >= 2 && it.cmdline.last() == "start" && it.cmdline[it.cmdline.size - 2].endsWith("nouride")
      }
      if (daemon != null) {
        event("SIGTERM to the daemon, pid ${daemon.pid}")
        Os.kill(daemon.pid, OsConstants.SIGTERM)
      }
      if (!waitFor(process, if (daemon != null) 20_000 else 0)) {
        event("SIGQUIT to proot, pid ${proot.pid}")
        Os.kill(proot.pid, OsConstants.SIGQUIT)
      }
    }
    if (!waitFor(process, 5_000)) process.destroy()
  }

  private fun waitFor(process: Process, timeoutMs: Long): Boolean {
    val deadline = SystemClock.elapsedRealtime() + timeoutMs
    while (true) {
      try {
        process.exitValue()
        return true
      } catch (e: IllegalThreadStateException) {
        if (SystemClock.elapsedRealtime() >= deadline) return false
        Thread.sleep(200)
      }
    }
  }

  private fun onHealth(health: Health) {
    val now = System.currentTimeMillis()
    ServerState.setHealth(health, now)
    append(layout.healthLog, "${timestamp(now)} ${health.httpCode} ${health.summary()}")
    if (health.ok && ServerState.phase == ServerState.Phase.STARTING) {
      ServerState.set(ServerState.Phase.RUNNING, "ready")
    }
    showNotification()
  }

  private fun acquireLocks() {
    if (wakeLock == null) {
      wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "nouride:server")
        .apply { setReferenceCounted(false); acquire() }
    }
    if (wifiLock == null) {
      @Suppress("DEPRECATION")
      wifiLock = (applicationContext.getSystemService(WIFI_SERVICE) as WifiManager)
        .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "nouride:server")
        .apply { setReferenceCounted(false); acquire() }
    }
  }

  private fun releaseLocks() {
    wakeLock?.release()
    wakeLock = null
    wifiLock?.release()
    wifiLock = null
  }

  private fun showNotification() {
    val open = PendingIntent.getActivity(
      this, 0, Intent(this, StatusActivity::class.java),
      PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0),
    )
    val health = ServerState.health
    val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else {
      @Suppress("DEPRECATION")
      Notification.Builder(this)
    }
    val notification = builder
      .setSmallIcon(android.R.drawable.stat_sys_upload_done)
      .setContentTitle("Nouride: ${ServerState.phase.name.lowercase()}")
      .setContentText(if (ServerState.phase == ServerState.Phase.RUNNING && health != null) health.summary() else ServerState.detail)
      .setContentIntent(open)
      .setOngoing(true)
      .build()
    if (Build.VERSION.SDK_INT >= 34) {
      startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
  }

  private fun event(message: String) = append(layout.serviceLog, "${timestamp(System.currentTimeMillis())} $message")

  private fun deleteTree(file: File) {
    if (!AndroidFileOps.isSymlink(file.path) && file.isDirectory) {
      Os.chmod(file.path, "700".toInt(8))
      file.listFiles()?.forEach(::deleteTree)
    }
    file.delete()
  }

  companion object {
    const val ACTION_START = "tech.nouverse.nouride.android.START"
    const val ACTION_STOP = "tech.nouverse.nouride.android.STOP"
    const val PREFS = "server"
    const val PREF_RUN = "run"
    private const val CHANNEL = "server"
    private const val NOTIFICATION_ID = 1
    private const val ROOTFS_ASSET = "rootfs.tgz"
    private const val HEALTH_INTERVAL_SECONDS = 60L

    fun start(context: Context) = send(context, ACTION_START)

    fun stop(context: Context) = send(context, ACTION_STOP)

    private fun send(context: Context, action: String) {
      val intent = Intent(context, ServerService::class.java).setAction(action)
      if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
    }

    private fun readable(path: String): Boolean = try {
      File(path).inputStream().use { it.read() }
      true
    } catch (e: IOException) {
      false
    }

    private fun command(vararg argv: String): String =
      ProcessBuilder(*argv).redirectErrorStream(true).start().let { p -> p.inputStream.bufferedReader().use { it.readText() }.also { p.waitFor() } }

    private val lock = Any()

    private fun append(file: File, line: String) = synchronized(lock) {
      file.parentFile?.mkdirs()
      file.appendText(line + "\n")
    }

    private fun timestamp(millis: Long): String =
      SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date(millis))
  }
}
