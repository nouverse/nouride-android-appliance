package tech.nouverse.nouride.android.install

import java.io.File

/** The app's own uid, gid and groups with their Android names, as `/system/bin/id` prints them. */
data class AndroidIds(val user: String, val uid: Int, val gid: Int, val groups: List<Pair<Int, String>>) {
  companion object {
    /** Parses `uid=10304(u0_a304) gid=10304(u0_a304) groups=10304(u0_a304),3003(inet),...`. */
    fun parse(idOutput: String): AndroidIds? {
      val field = { key: String -> Regex("$key=([^ ]+)").find(idOutput)?.groupValues?.get(1) }
      val ids = { text: String? ->
        Regex("(\\d+)\\(([^)]+)\\)").findAll(text ?: "").map { it.groupValues[1].toInt() to it.groupValues[2] }.toList()
      }
      val uid = ids(field("uid")).firstOrNull() ?: return null
      val gid = ids(field("gid")).firstOrNull() ?: return null
      return AndroidIds(uid.second, uid.first, gid.first, ids(field("groups")).ifEmpty { listOf(gid) })
    }
  }
}

/**
 * What proot-distro does to a rootfs after unpacking it: public DNS servers (Android has no
 * resolv.conf to share), a plain hosts file, the app's own Android ids as `aid_` entries, and a
 * world-writable /tmp.
 */
object RootfsSetup {
  const val RESOLV_CONF = "nameserver 8.8.8.8\nnameserver 8.8.4.4\n"
  const val HOSTS = "127.0.0.1   localhost.localdomain localhost\n" +
    "::1         localhost.localdomain localhost ip6-localhost ip6-loopback\n"

  fun apply(rootfs: File, ids: AndroidIds?, fs: FileOps) {
    val etc = File(rootfs, "etc")
    for (name in listOf("resolv.conf", "hosts")) {
      val file = File(etc, name)
      if (fs.isSymlink(file.path) || file.exists()) file.delete()
    }
    File(etc, "resolv.conf").writeText(RESOLV_CONF)
    File(etc, "hosts").writeText(HOSTS)

    for (name in listOf("passwd", "shadow", "group", "gshadow")) fs.chmod(File(etc, name).path, "644".toInt(8))
    if (ids != null) {
      val user = "aid_${ids.user}"
      File(etc, "passwd").appendText("$user:x:${ids.uid}:${ids.gid}:Android app:/:/sbin/nologin\n")
      File(etc, "shadow").appendText("$user:*:18446:0:99999:7:::\n")
      for ((gid, name) in ids.groups) {
        File(etc, "group").appendText("aid_$name:x:$gid:root,$user\n")
        File(etc, "gshadow").appendText("aid_$name:*::root,$user\n")
      }
    }

    val tmp = File(rootfs, "tmp")
    tmp.mkdirs()
    fs.chmod(tmp.path, "1777".toInt(8))
    File(rootfs, ".l2s").mkdirs()
  }
}
