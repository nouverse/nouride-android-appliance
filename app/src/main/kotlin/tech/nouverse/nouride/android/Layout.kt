package tech.nouverse.nouride.android

import android.content.Context
import tech.nouverse.nouride.android.server.ProotPaths
import java.io.File

/** Where everything lives in the app's private storage. */
class Layout(context: Context) {
  private val files: File = context.filesDir
  val distro = File(files, "debian")
  val rootfs = File(distro, "rootfs")
  val rootfsPartial = File(distro, "rootfs.partial")
  val sysdata = File(distro, "sysdata")
  val shm = File(distro, "shm")
  val prootTmp = File(files, "proot-tmp")
  val logs = File(files, "logs")
  val serviceLog = File(logs, "service.log")
  val installLog = File(logs, "install.log")
  val daemonLog = File(logs, "daemon.log")
  val healthLog = File(logs, "health.log")
  private val nativeLibs = File(context.applicationInfo.nativeLibraryDir)

  val installed: Boolean get() = File(rootfs, "srv/nouride/nouride").isFile

  fun prootPaths() = ProotPaths(
    proot = File(nativeLibs, "libproot.so").path,
    loader = File(nativeLibs, "libproot-loader.so").path,
    rootfs = rootfs.path,
    sysdata = sysdata.path,
    shm = shm.path,
    tmp = prootTmp.path,
  )
}
