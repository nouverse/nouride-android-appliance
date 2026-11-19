package tech.nouverse.nouride.android.backup

import android.content.Context
import android.os.Handler
import android.os.Looper
import tech.nouverse.nouride.android.Layout
import tech.nouverse.nouride.android.Nouride
import tech.nouverse.nouride.android.backup.BackupStatus.Step
import tech.nouverse.nouride.android.download.DownloadsFolder
import tech.nouverse.nouride.android.server.ProotArgs
import tech.nouverse.nouride.android.server.ProotRunner
import tech.nouverse.nouride.android.server.ServerService
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * "Download Nouride's files" on More: the phone's own Nouride as one zip in Downloads (`spec/backup.md`). It counts what
 * goes in when More is shown, then, on the button, has Nouride take its database's snapshot inside Debian
 * (`nouride db backup --json`, safe while it runs) and streams the folder and that snapshot into the zip. Held here,
 * not by the screen: a zip of a few hundred megabytes outlives a turn of the phone.
 */
object NourideBackup {
  @Volatile
  var status = BackupStatus()
    private set

  private val main = Handler(Looper.getMainLooper())
  private val listeners = mutableSetOf<() -> Unit>()

  /** Told on the main thread whenever [status] changes. */
  fun listen(listener: () -> Unit) {
    listeners += listener
  }

  fun unlisten(listener: () -> Unit) {
    listeners -= listener
  }

  private fun set(next: BackupStatus) {
    status = next
    main.post { listeners.toList().forEach { it() } }
  }

  /** The `.nouride` folder in the phone's Debian. */
  private fun folder(layout: Layout) = File(layout.rootfs, Nouride.DATA_DIR.removePrefix("/"))

  /** What the zip may weigh: counted again whenever More is shown and nothing is being made. */
  fun count(context: Context) {
    if (status.busy) return
    val layout = Layout(context)
    thread(name = "backup-count") {
      val root = folder(layout)
      if (!root.isDirectory) return@thread
      val size = Backup.plan(root).bytes + database(root)
      set(status.copy(size = size, sealed = File(root, SEALED).isFile))
    }
  }

  /** The zip, from the snapshot to the line that says where it is. */
  fun start(context: Context) {
    if (status.busy) return
    val app = context.applicationContext
    val layout = Layout(app)
    set(status.copy(step = Step.SNAPSHOT, done = 0, total = 0))
    thread(name = "backup") {
      val root = folder(layout)
      val snapshot = snapshot(layout)?.let { File(layout.rootfs, it.removePrefix("/")) }?.takeIf { it.isFile }
      if (snapshot == null) return@thread set(status.copy(step = Step.NO_SNAPSHOT))
      val plan = Backup.plan(root)
      val total = plan.bytes + snapshot.length()
      val downloads = DownloadsFolder(app)
      if (!downloads.fits(total)) return@thread set(status.copy(step = Step.NO_ROOM, size = total))
      set(status.copy(step = Step.SAVING, size = total, sealed = File(root, SEALED).isFile, done = 0, total = total))
      val name = Backup.zipName(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
      val saved = downloads.save(name) { out ->
        Backup.write(plan, snapshot, out) { done -> if (done - status.done >= STEP || done == total) set(status.copy(done = done)) }
      }
      set(
        when (saved) {
          is DownloadsFolder.Saved.Done -> status.copy(step = Step.SAVED, saved = saved.name, done = total)
          DownloadsFolder.Saved.NoRoom -> status.copy(step = Step.NO_ROOM)
          DownloadsFolder.Saved.Failed -> status.copy(step = Step.FAILED)
        },
      )
    }
  }

  /** The snapshot Nouride took, as a path in Debian, or null. Its answer goes to the app's log for Technical Logs. */
  private fun snapshot(layout: Layout): String? {
    val args = ProotArgs(layout.prootPaths(), exists = { File(it).exists() }, readable = { ServerService.readable(it) })
    val output = StringBuilder()
    return try {
      val code = ProotRunner(args, layout.prootTmp).run(Nouride.DB_BACKUP, layout.backupLog) { output.appendLine(it) }
      if (code == 0) Backup.snapshot(output.toString()) else null
    } catch (e: IOException) {
      null
    }
  }

  /** The database as it is now, its write-ahead log with it: about what its snapshot will weigh. */
  private fun database(root: File): Long = listOf("data/nouride.db", "data/nouride.db-wal").sumOf { File(root, it).length() }

  private const val SEALED = "data/secrets.sealed"

  /** How often the bar moves: every 4 MiB read. */
  private const val STEP = 4L * 1024 * 1024
}
