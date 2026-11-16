package tech.nouverse.nouride.android.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProotArgsTest {
  private val paths = ProotPaths(
    proot = "/lib/libproot.so",
    loader = "/lib/libproot-loader.so",
    rootfs = "/files/debian/rootfs",
    sysdata = "/files/debian/sysdata",
    shm = "/files/debian/shm",
    tmp = "/files/proot-tmp",
  )

  @Test
  fun `the invocation proot-distro uses, with fake proc files where the host denies them`() {
    val denied = setOf("/proc/loadavg", "/proc/stat", "/proc/vmstat", "/proc/sys/kernel/cap_last_cap")
    val command = ProotArgs(paths, exists = { true }, readable = { it !in denied })
      .build(listOf("/bin/su", "-s", "/bin/sh", "nouride", "-c", "cd /srv/nouride && exec ./nouride start"))

    assertEquals(
      listOf(
        "/lib/libproot.so",
        "--kill-on-exit",
        "--link2symlink",
        "--sysvipc",
        "--kernel-release=\\Linux\\localhost\\6.17.0-PRoot-Distro\\#1 SMP PREEMPT_DYNAMIC Fri, 10 Oct 2025 00:00:00 +0000\\aarch64\\localdomain\\-1\\",
        "-L",
        "--change-id=0:0",
        "--rootfs=/files/debian/rootfs",
        "--cwd=/root",
        "--bind=/dev",
        "--bind=/proc",
        "--bind=/sys",
        "--bind=/dev/urandom:/dev/random",
        "--bind=/files/debian/sysdata/sys_empty:/sys/fs/selinux",
        "--bind=/files/debian/sysdata/loadavg:/proc/loadavg",
        "--bind=/files/debian/sysdata/stat:/proc/stat",
        "--bind=/files/debian/sysdata/vmstat:/proc/vmstat",
        "--bind=/files/debian/sysdata/sysctl_entry_cap_last_cap:/proc/sys/kernel/cap_last_cap",
        "--bind=/files/debian/shm:/dev/shm",
        "/bin/su", "-s", "/bin/sh", "nouride", "-c", "cd /srv/nouride && exec ./nouride start",
      ),
      command.argv,
    )
  }

  @Test
  fun `proot gets its loader, temp dir and link2symlink dir from the environment`() {
    val env = ProotArgs(paths, exists = { true }, readable = { true }).build(listOf("/bin/true")).env
    assertEquals("/lib/libproot-loader.so", env["PROOT_LOADER"])
    assertEquals("/files/proot-tmp", env["PROOT_TMP_DIR"])
    assertEquals("/files/debian/rootfs/.l2s", env["PROOT_L2S_DIR"])
    assertEquals(GUEST_PATH, env["PATH"])
  }

  @Test
  fun `device links the host lacks are bound from proc`() {
    val argv = ProotArgs(paths, exists = { it != "/dev/fd" && it != "/dev/stderr" }, readable = { true })
      .build(listOf("/bin/true")).argv
    assertTrue("--bind=/proc/self/fd:/dev/fd" in argv)
    assertTrue("--bind=/proc/self/fd/2:/dev/stderr" in argv)
    assertFalse(argv.any { it.endsWith(":/dev/stdin") })
  }
}
