package tech.nouverse.nouride.android.backup

import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Nouride's files as one zip (`spec/backup.md`): what under `.nouride` stays out of it, the database snapshot it carries
 * in place of the open database, its name, and the zip itself, written as it is read. The desktop's `backup` crate
 * holds the same list, tested against the same fixture.
 */
object Backup {
  /** Where the folder's files go in the zip: it unpacks where the folder was. */
  const val FOLDER_ENTRY = ".nouride/"

  /** The database snapshot's place in the zip: where the open database is in the folder. */
  const val DATABASE_ENTRY = ".nouride/data/nouride.db"

  /**
   * What stays out, as path patterns under `.nouride/`: a pattern ending in `/` is a folder and everything in it, `*`
   * any part of one name, `**` + `/` any number of folders. Why each is out is in `spec/fixtures/backup.json`.
   */
  val OUT = listOf(
    // Secrets in the clear, and what opens them. Only `data/secrets.sealed` goes in.
    "data/secrets.enc",
    "data/secrets.json",
    "encryption.key",
    "data/.control-token",
    "credentials/",
    "home/",
    "workspace/private/*/.cli/",
    "data/whatsapp/",
    "data/appliance/",
    "data/cli-accounts.json",
    "nougate/",
    // The open database and what comes with it: the snapshot goes in instead.
    "data/nouride.*",
    "data/backups/",
    // What the installer or Nouride puts back.
    "bin/",
    "lib/",
    "opt/",
    "share/",
    "system-packages/",
    ".crates.toml",
    ".crates2.json",
    "**/node_modules/",
    "**/.venv/",
    "**/__pycache__/",
    // Logs and caches.
    "jobs/",
    "tool-output/",
    "data/knowledge/",
    "data/clean-shutdown.json",
    "workspace/private/*/.scratchpad/",
  )

  /** Whether [path] (under `.nouride/`, `/` between names, ending in `/` for a folder) goes in the zip. */
  fun kept(path: String): Boolean = OUT.none { matches(it, path) }

  /** The zip's name on [date] (`YYYY-MM-DD`, the phone's own). */
  fun zipName(date: String): String = "nouride-backup-$date.zip"

  /** The snapshot `nouride db backup --json` took, from what it printed: `data.path` of its JSON line when `ok`. */
  fun snapshot(output: String): String? = output.lines().asReversed().firstNotNullOfOrNull { line ->
    val answer = try {
      JSONObject(line.trim())
    } catch (e: Exception) {
      return@firstNotNullOfOrNull null
    }
    if (answer.optBoolean("ok", false)) answer.optJSONObject("data")?.optString("path", "")?.takeIf { it.isNotEmpty() } else null
  }

  private fun matches(pattern: String, path: String): Boolean {
    val folderPattern = pattern.endsWith("/")
    val isFolder = path.endsWith("/")
    val pat = pattern.trimEnd('/').split('/')
    val names = path.trimEnd('/').split('/')
    if (!folderPattern) return !isFolder && segmentsMatch(pat, names)
    // A folder pattern takes the folder and everything in it: any leading run of names that is a folder.
    val last = if (isFolder) names.size else names.size - 1
    return (1..last).any { segmentsMatch(pat, names.subList(0, it)) }
  }

  private fun segmentsMatch(pat: List<String>, names: List<String>): Boolean {
    if (pat.isEmpty()) return names.isEmpty()
    val first = pat[0]
    val rest = pat.subList(1, pat.size)
    if (first == "**") return (0..names.size).any { segmentsMatch(rest, names.subList(it, names.size)) }
    return names.isNotEmpty() && nameMatches(first, names[0]) && segmentsMatch(rest, names.subList(1, names.size))
  }

  /** One name against one part of a pattern, `*` standing for any run of characters. */
  private fun nameMatches(pattern: String, name: String): Boolean {
    val star = pattern.indexOf('*')
    if (star < 0) return pattern == name
    val head = pattern.substring(0, star)
    val tail = pattern.substring(star + 1)
    if (!name.startsWith(head)) return false
    val rest = name.substring(head.length)
    return (0..rest.length).any { nameMatches(tail, rest.substring(it)) }
  }

  /** A file that goes in: its path under `.nouride/`, where it is, its size when it was counted. */
  data class Entry(val name: String, val file: File, val bytes: Long)

  /** What the zip will hold of the folder, counted before it starts. */
  data class Plan(val files: List<Entry>, val bytes: Long)

  /**
   * Every file under [root] (the `.nouride` folder) that goes in, links never followed. A file or folder that cannot be
   * read is left out rather than stopping the count; the open database's size is the caller's to add.
   */
  fun plan(root: File): Plan {
    val files = mutableListOf<Entry>()
    walk(root, "", files)
    files.sortBy { it.name }
    return Plan(files, files.sumOf { it.bytes })
  }

  private fun walk(dir: File, under: String, into: MutableList<Entry>) {
    for (each in (dir.listFiles() ?: return).sortedBy { it.name }) {
      if (Files.isSymbolicLink(each.toPath())) continue
      if (Files.isDirectory(each.toPath(), LinkOption.NOFOLLOW_LINKS)) {
        val folder = "$under${each.name}/"
        if (kept(folder)) walk(each, folder, into)
      } else if (Files.isRegularFile(each.toPath(), LinkOption.NOFOLLOW_LINKS)) {
        val file = "$under${each.name}"
        if (kept(file)) into += Entry(file, each, each.length())
      }
    }
  }

  /**
   * The zip into [out]: the plan's files under [FOLDER_ENTRY], then the database snapshot as [DATABASE_ENTRY], each
   * streamed in. [progress] hears the bytes read so far. A file gone since it was counted is skipped; any other error
   * stops it.
   */
  fun write(plan: Plan, database: File, out: OutputStream, progress: (Long) -> Unit = {}) {
    val zip = ZipOutputStream(out)
    // Fast over small: the database and text compress well at the lowest level, and a phone is slow at the highest.
    zip.setLevel(Deflater.BEST_SPEED)
    var done = 0L
    val buf = ByteArray(256 * 1024)
    fun copy(from: FileInputStream) = from.use { input ->
      while (true) {
        val n = input.read(buf)
        if (n < 0) break
        zip.write(buf, 0, n)
        done += n
        progress(done)
      }
    }
    for (entry in plan.files) {
      val input = try {
        FileInputStream(entry.file)
      } catch (e: IOException) {
        continue
      }
      // Each file keeps its own time, not the zip's.
      zip.putNextEntry(ZipEntry(FOLDER_ENTRY + entry.name).apply { time = entry.file.lastModified() })
      copy(input)
      zip.closeEntry()
    }
    zip.putNextEntry(ZipEntry(DATABASE_ENTRY).apply { time = database.lastModified() })
    copy(FileInputStream(database))
    zip.closeEntry()
    zip.finish()
  }
}
