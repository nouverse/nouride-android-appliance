package tech.nouverse.nouride.android.server

import java.io.File

/**
 * The app's processes as /proc shows them. Android mounts /proc with hidepid, so an app sees only
 * its own uid's processes: proot and everything it runs, which is all this needs.
 */
object Proc {
  data class Entry(val pid: Int, val ppid: Int, val cmdline: List<String>)

  fun list(proc: File = File("/proc")): List<Entry> =
    proc.listFiles { f -> f.name.all(Char::isDigit) }.orEmpty().mapNotNull { dir ->
      try {
        val ppid = parsePpid(File(dir, "stat").readText()) ?: return@mapNotNull null
        val cmdline = File(dir, "cmdline").readText().split('\u0000').filter { it.isNotEmpty() }
        Entry(dir.name.toInt(), ppid, cmdline)
      } catch (e: java.io.IOException) {
        null // ended while being read
      }
    }

  /** The parent pid from /proc/<pid>/stat; the name field before it may hold spaces and parentheses. */
  fun parsePpid(stat: String): Int? =
    stat.substringAfterLast(')', "").trim().split(' ').getOrNull(1)?.toIntOrNull()

  /** [root] and every process below it. */
  fun descendants(entries: List<Entry>, root: Int): List<Entry> {
    val byParent = entries.groupBy { it.ppid }
    val result = mutableListOf<Entry>()
    val queue = ArrayDeque(listOf(root))
    while (queue.isNotEmpty()) {
      for (child in byParent[queue.removeFirst()].orEmpty()) {
        result += child
        queue += child.pid
      }
    }
    return result
  }
}
