package tech.nouverse.nouride.android.update

import java.io.File
import java.security.MessageDigest

/**
 * The APK an earlier tap on Update downloaded and checked, kept until it is installed. The owner, 2026-11-07: Android's
 * installer asked for a scan, a browser opened by mistake, and back in the app Update downloaded all 50 MB again.
 * Interrupted or cancelled, an install now starts again from the file already here, as long as it still has the
 * release's sha256.
 */
object Downloaded {
  fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
      val buffer = ByteArray(1 shl 16)
      while (true) {
        val n = input.read(buffer)
        if (n < 0) break
        digest.update(buffer, 0, n)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  /** [name] from an earlier download when its sha256 is still [expected], else null; anything else in [dir] goes. */
  fun keep(dir: File, name: String, expected: String): File? {
    dir.listFiles()?.filter { it.name != name }?.forEach { it.delete() }
    val file = File(dir, name)
    if (!file.isFile) return null
    if (sha256(file) == expected.lowercase()) return file
    file.delete()
    return null
  }
}
