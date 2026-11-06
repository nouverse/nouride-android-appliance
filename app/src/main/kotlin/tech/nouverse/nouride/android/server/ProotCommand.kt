package tech.nouverse.nouride.android.server

/** The release the guest's `uname -r` reports: the recent one proot-distro 5.10 fakes too. */
const val KERNEL_RELEASE = "6.17.0-PRoot-Distro"
const val KERNEL_VERSION = "#1 SMP PREEMPT_DYNAMIC Fri, 10 Oct 2025 00:00:00 +0000"

/** PATH inside the guest, Debian's default for root. */
const val GUEST_PATH = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

data class ProotCommand(val argv: List<String>, val env: Map<String, String>)

/** Where proot and the guest's host-side directories are on the phone. */
data class ProotPaths(
  val proot: String,
  val loader: String,
  val rootfs: String,
  val sysdata: String,
  val shm: String,
  val tmp: String,
)

/**
 * The proot invocation of `proot-distro login debian` (5.10, on Termux), rebuilt for this app's
 * paths: the one Nouride ran under on the Redmi 12 on 2026-11-05. The guest runs as faked root;
 * [exists] and [readable] answer for the host, so the device binds and fake /proc files are
 * chosen the way proot-distro chooses them.
 */
class ProotArgs(
  private val paths: ProotPaths,
  private val exists: (String) -> Boolean,
  private val readable: (String) -> Boolean,
) {
  fun build(command: List<String>, cwd: String = "/root"): ProotCommand {
    val argv = mutableListOf(
      paths.proot,
      "--kill-on-exit",
      "--link2symlink",
      "--sysvipc",
      "--kernel-release=\\Linux\\localhost\\$KERNEL_RELEASE\\$KERNEL_VERSION\\aarch64\\localdomain\\-1\\",
      "-L",
      "--change-id=0:0",
      "--rootfs=${paths.rootfs}",
      "--cwd=$cwd",
      "--bind=/dev",
      "--bind=/proc",
      "--bind=/sys",
      "--bind=/dev/urandom:/dev/random",
    )
    if (!exists("/dev/fd")) argv += "--bind=/proc/self/fd:/dev/fd"
    listOf("stdin", "stdout", "stderr").forEachIndexed { fd, name ->
      if (!exists("/dev/$name")) argv += "--bind=/proc/self/fd/$fd:/dev/$name"
    }
    argv += "--bind=${paths.sysdata}/$SYS_EMPTY:/sys/fs/selinux"
    for (entry in SysData.ENTRIES) {
      if (!readable(entry.hostPath)) argv += "--bind=${paths.sysdata}/${entry.name}:${entry.hostPath}"
    }
    argv += "--bind=${paths.shm}:/dev/shm"
    argv += command

    val env = linkedMapOf(
      "PROOT_LOADER" to paths.loader,
      "PROOT_TMP_DIR" to paths.tmp,
      "PROOT_L2S_DIR" to "${paths.rootfs}/.l2s",
      "HOME" to "/root",
      "PATH" to GUEST_PATH,
      "LANG" to "C.UTF-8",
      "TMPDIR" to "/tmp",
    )
    return ProotCommand(argv, env)
  }

  companion object {
    /** An empty directory bound over /sys/fs/selinux, so the guest does not see SELinux enforcing. */
    const val SYS_EMPTY = "sys_empty"
  }
}
