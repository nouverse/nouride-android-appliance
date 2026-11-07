package tech.nouverse.nouride.android.server

import java.io.File

/**
 * Stand-ins for the /proc files an app may not read on Android (SELinux denies them), which
 * Debian's tools and the daemon expect. The same set proot-distro 5.10 writes; the contents are
 * plain placeholders, except that the CPU list, uptime and boot time are the phone's.
 */
object SysData {
  data class Entry(val name: String, val hostPath: String)

  val ENTRIES = listOf(
    Entry("loadavg", "/proc/loadavg"),
    Entry("stat", "/proc/stat"),
    Entry("uptime", "/proc/uptime"),
    Entry("version", "/proc/version"),
    Entry("vmstat", "/proc/vmstat"),
    Entry("sysctl_entry_cap_last_cap", "/proc/sys/kernel/cap_last_cap"),
    Entry("sysctl_inotify_max_user_watches", "/proc/sys/fs/inotify/max_user_watches"),
    Entry("sysctl_kernel_overflowuid", "/proc/sys/kernel/overflowuid"),
    Entry("sysctl_kernel_overflowgid", "/proc/sys/kernel/overflowgid"),
  )

  /** Every CPU idle since boot, so tools that divide by the totals get no zero. */
  fun contents(cpuIds: List<Int>, uptimeSeconds: Long, bootTimeEpochSeconds: Long): Map<String, String> {
    val cpus = cpuIds.ifEmpty { listOf(0) }
    val idleTicks = uptimeSeconds * USER_HZ
    val stat = buildString {
      append("cpu  0 0 0 ${idleTicks * cpus.size} 0 0 0 0 0 0\n")
      for (cpu in cpus) append("cpu$cpu 0 0 0 $idleTicks 0 0 0 0 0 0\n")
      append("intr 0\nctxt 0\nbtime $bootTimeEpochSeconds\nprocesses 0\n")
      append("procs_running 1\nprocs_blocked 0\nsoftirq 0 0 0 0 0 0 0 0 0 0 0\n")
    }
    return mapOf(
      "loadavg" to "0.00 0.00 0.00 1/1 1\n",
      "stat" to stat,
      "uptime" to "$uptimeSeconds.00 ${uptimeSeconds * cpus.size}.00\n",
      "version" to "Linux version $KERNEL_RELEASE (nouride@android) $KERNEL_VERSION\n",
      "vmstat" to VMSTAT_KEYS.joinToString("") { "$it 0\n" },
      "sysctl_entry_cap_last_cap" to "40\n",
      "sysctl_inotify_max_user_watches" to "8192\n",
      "sysctl_kernel_overflowuid" to "65534\n",
      "sysctl_kernel_overflowgid" to "65534\n",
    )
  }

  /** The ids of the online CPUs, from the `processor : N` lines of /proc/cpuinfo. */
  fun cpuIds(cpuinfo: String): List<Int> =
    cpuinfo.lineSequence()
      .map { it.split(':', limit = 2) }
      .filter { it.size == 2 && it[0].trim() == "processor" }
      .mapNotNull { it[1].trim().toIntOrNull() }
      .toList()

  fun write(dir: File, contents: Map<String, String>) {
    File(dir, ProotArgs.SYS_EMPTY).mkdirs()
    for ((name, text) in contents) File(dir, name).writeText(text)
  }

  private const val USER_HZ = 100L
  private val VMSTAT_KEYS = listOf(
    "nr_free_pages", "nr_inactive_anon", "nr_active_anon", "nr_inactive_file", "nr_active_file",
    "nr_dirty", "nr_writeback", "pgpgin", "pgpgout", "pswpin", "pswpout", "pgfault", "pgmajfault",
  )
}
