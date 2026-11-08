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
import tech.nouverse.nouride.android.ui.StatusLook
import tech.nouverse.nouride.android.ui.title
import tech.nouverse.nouride.android.update.Updates
import tech.nouverse.nouride.android.R
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
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
  private var updateChecks: ScheduledExecutorService? = null

  @Volatile
  private var supervisor: Supervisor? = null

  @Volatile
  private var current: Process? = null

  @Volatile
  private var stopping = false

  /** Only on the first start, the one a person waits minutes for. */
  @Volatile
  private var setup: SetupProgress? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    layout = Layout(this)
    val args = ProotArgs(layout.prootPaths(), exists = { File(it).exists() }, readable = ::readable)
    runner = ProotRunner(args, layout.prootTmp)
    if (Build.VERSION.SDK_INT >= 26) {
      getSystemService(NotificationManager::class.java)
        .createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW))
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    // Within seconds of startForegroundService(), whatever the intent asks.
    showNotification()
    if (intent?.action == ACTION_STOP) {
      stopServer()
      return START_NOT_STICKY
    }
    if (intent?.action == ACTION_RESTART) {
      restartDaemon()
      return START_STICKY
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
    // Twice a day, for a person who never opens the app: the notification is where they see an update of it.
    if (updateChecks == null) {
      updateChecks = Executors.newSingleThreadScheduledExecutor { Thread(it, "update-check").apply { isDaemon = true } }.also {
        it.scheduleWithFixedDelay({
          try {
            Updates.check(this, UPDATE_CHECK_HOURS * 3_600_000L)
            showNotification()
          } catch (e: Exception) {
            event("update check failed: $e")
          }
        }, 1, UPDATE_CHECK_HOURS * 60, TimeUnit.MINUTES)
      }
    }
  }

  private fun runServer() {
    try {
      setup = if (!layout.rootfs.isDirectory || !layout.installed) SetupProgress() else null
      if (!layout.rootfs.isDirectory) extractRootfs()
      prepareHostDirs()
      if (!layout.installed) {
        installNouride()
        if (!stopping) giveAccount()
      }
      if (!stopping) superviseDaemon()
    } catch (e: Exception) {
      event("failed: $e")
      setup = null
      ServerState.set(ServerState.Phase.FAILED, e.message ?: e.toString(), progress = null)
      showNotification()
    }
  }

  private fun extractRootfs() {
    ServerState.set(ServerState.Phase.EXTRACTING, "unpacking Debian", setup?.snapshot())
    showNotification()
    if (layout.rootfsPartial.exists()) deleteTree(layout.rootfsPartial)
    val began = SystemClock.elapsedRealtime()
    // Stored uncompressed in the APK (noCompress), so its length is known before reading it.
    val total = assets.openFd(ROOTFS_ASSET).use { it.length }
    val count = Counting(assets.open(ROOTFS_ASSET)).use { input ->
      RootfsExtractor(layout.rootfsPartial, AndroidFileOps).extract(input) { n ->
        setup?.unpacked(input.read, total)
        ServerState.set(ServerState.Phase.EXTRACTING, "unpacking Debian: $n files", setup?.snapshot())
        showNotification()
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
    setup?.line(SetupProgress.marker(Step.PACKAGES))
    ServerState.set(ServerState.Phase.INSTALLING, "running the Nouride installer", setup?.snapshot())
    showNotification()
    event("installer started")
    var shown = -1
    val process = runner.start(Nouride.INSTALL, layout.installLog) { line ->
      setup?.line(line)
      ServerState.set(ServerState.Phase.INSTALLING, line.take(160), setup?.snapshot())
      // The notification only when the percent changes: apt prints hundreds of lines.
      val percent = setup?.snapshot()?.percent ?: -1
      if (percent != shown) {
        shown = percent
        showNotification()
      }
    }
    current = process
    val code = process.waitFor()
    current = null
    event("installer exited with $code")
    if (stopping) return
    if (code != 0 || !layout.installed) throw IOException("the installer exited with $code; see install.log")
  }

  /** The account the dashboard on the phone signs in with (`FirstAdmin`): only on the install that makes it. */
  private fun giveAccount() {
    val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
    val password = prefs.getString(PREF_ADMIN_PASSWORD, null) ?: FirstAdmin.newPassword().also {
      prefs.edit().putString(PREF_ADMIN_PASSWORD, it).commit()
    }
    val env = if (layout.installEnv.isFile) layout.installEnv.readText() else ""
    layout.installEnv.writeText(FirstAdmin.withAccount(env, password))
    Os.chmod(layout.installEnv.path, "600".toInt(8))
    event("dashboard account handed to the daemon")
  }

  /** Once the daemon is ready the account exists, so the password leaves its `.env`. */
  private fun takeAccountBack() {
    val env = layout.installEnv
    if (!env.isFile) return
    val text = env.readText()
    val without = FirstAdmin.withoutAccount(text)
    if (without != text) {
      env.writeText(without)
      event("dashboard account taken out of .env")
    }
  }

  private fun superviseDaemon() {
    val supervisor = Supervisor(
      start = {
        runner.start(Nouride.START, layout.daemonLog) { line ->
          if (line.contains(Nouride.READY_LINE)) {
            event("daemon ready")
            takeAccountBack()
            ServerState.set(ServerState.Phase.RUNNING, "ready")
            showNotification()
            poller?.checkNow()
          }
        }
      },
      backoff = Backoff(),
      clock = SystemClock::elapsedRealtime,
      onStarted = { process ->
        current = process
        event("daemon started")
        setup?.starting()
        ServerState.set(ServerState.Phase.STARTING, "starting the daemon", setup?.snapshot())
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

  /**
   * The daemon stopped cleanly and started again at once, to read its `.env` again: the Connect tab changed who may
   * open Nouride. Nothing when Nouride is off: it reads the change when it is started.
   */
  private fun restartDaemon() {
    val process = current ?: return
    val supervisor = supervisor ?: return
    event("daemon restarting: who may open Nouride changed")
    supervisor.restartNext()
    thread(name = "nouride-restart") { terminate(process) }
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
    updateChecks?.shutdownNow()
    updateChecks = null
    releaseLocks()
    setup = null
    ServerState.set(ServerState.Phase.STOPPED, progress = null)
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
    // Answering is the end of the first start: the bar's last 1 % is the ready screen itself.
    if (health.ok && setup != null) {
      setup = null
      ServerState.set(ServerState.phase, ServerState.detail, progress = null)
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
    // The same sentence as the status screen, never a phase name or a log line: the users are not technical.
    val look = StatusLook.of(ServerState.phase, answering = ServerState.health?.ok == true)
    val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else {
      @Suppress("DEPRECATION")
      Notification.Builder(this)
    }
    val progress = ServerState.progress
    if (progress != null && look.busy) builder.setProgress(100, progress.percent, false)
    val notification = builder
      .setSmallIcon(R.drawable.ic_notification)
      .setColor(getColor(R.color.brand_text))
      .setContentTitle(getString(look.words.title()))
      .setContentText(getString(if (Updates.waiting(this) != null) R.string.notification_update else R.string.notification_tap))
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

  /** Counts the bytes read, for the unpacking step of the progress bar. */
  private class Counting(input: InputStream) : FilterInputStream(input) {
    @Volatile
    var read = 0L
      private set

    override fun read(): Int = super.read().also { if (it >= 0) read++ }

    override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) read += it }

    override fun skip(n: Long): Long = super.skip(n).also { read += it }
  }

  companion object {
    const val ACTION_START = "tech.nouverse.nouride.android.START"
    const val ACTION_STOP = "tech.nouverse.nouride.android.STOP"
    const val ACTION_RESTART = "tech.nouverse.nouride.android.RESTART"
    const val PREFS = "server"
    const val PREF_RUN = "run"
    const val PREF_ADMIN_PASSWORD = "admin_password"
    private const val CHANNEL = "server"
    private const val NOTIFICATION_ID = 1
    private const val ROOTFS_ASSET = "rootfs.tgz"
    private const val HEALTH_INTERVAL_SECONDS = 60L
    private const val UPDATE_CHECK_HOURS = 12L

    fun start(context: Context) = send(context, ACTION_START)

    fun stop(context: Context) = send(context, ACTION_STOP)

    /**
     * Who may open Nouride, written into its `.env`, and Nouride started again to read it if it is running. Off, it
     * reads it at its next start.
     */
    fun setOpenToOthers(context: Context, open: Boolean) {
      val env = Layout(context).installEnv
      if (!env.isFile) return
      val text = env.readText()
      val next = NetworkAccess.withAccess(text, open)
      if (next == text) return
      env.writeText(next)
      if (ServerState.phase == ServerState.Phase.RUNNING || ServerState.phase == ServerState.Phase.STARTING) {
        send(context, ACTION_RESTART)
      }
    }

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
