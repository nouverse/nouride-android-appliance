package tech.nouverse.nouride.android.install

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream

/** The file operations java.io lacks on API 24; android.system.Os on the phone. */
interface FileOps {
  fun symlink(target: String, path: String)
  fun chmod(path: String, mode: Int)
  fun isSymlink(path: String): Boolean
}

/**
 * Unpacks the rootfs tarball into [target]. Android refuses hard links in app storage, so a hard
 * link becomes a copy; device nodes and FIFOs are skipped (proot binds the host's /dev). Entries
 * that would land outside [target], or under a symlink, are refused.
 */
class RootfsExtractor(private val target: File, private val fs: FileOps) {
  private val checkedDirs = HashSet<String>()

  /** Returns the number of entries written; [progress] gets the count every 500 entries. */
  fun extract(gzip: InputStream, progress: (Int) -> Unit = {}): Int {
    target.mkdirs()
    val reader = TarReader(GZIPInputStream(gzip, 1 shl 16))
    val dirModes = mutableListOf<Pair<File, Int>>()
    var count = 0
    while (true) {
      val entry = reader.next() ?: break
      val relative = relativePath(entry.path) ?: continue
      val file = File(target, relative)
      ensureParent(relative)
      when (entry.type) {
        TarEntry.Type.DIRECTORY -> {
          if (!file.isDirectory && !file.mkdir()) throw IOException("cannot create directory $relative")
          dirModes += file to (entry.mode and 0xfff)
        }
        TarEntry.Type.FILE -> {
          replace(file)
          FileOutputStream(file).use { reader.data().copyTo(it, 1 shl 16) }
          fs.chmod(file.path, entry.mode and 0xfff)
          file.setLastModified(entry.mtimeSeconds * 1000)
        }
        TarEntry.Type.SYMLINK -> {
          replace(file)
          fs.symlink(entry.linkTarget, file.path)
        }
        TarEntry.Type.HARDLINK -> {
          val source = File(target, relativePath(entry.linkTarget) ?: throw IOException("bad hard link ${entry.path}"))
          if (fs.isSymlink(source.path)) throw IOException("hard link ${entry.path} to a symlink")
          replace(file)
          FileInputStream(source).use { input -> FileOutputStream(file).use { input.copyTo(it, 1 shl 16) } }
          fs.chmod(file.path, entry.mode and 0xfff)
        }
        TarEntry.Type.OTHER -> continue
      }
      count++
      if (count % 500 == 0) progress(count)
    }
    // Last, so a directory without write permission for its owner is filled first.
    for ((dir, mode) in dirModes.asReversed()) fs.chmod(dir.path, mode)
    return count
  }

  private fun replace(file: File) {
    if (fs.isSymlink(file.path) || file.isFile) {
      if (!file.delete()) throw IOException("cannot replace ${file.path}")
    }
  }

  /** No parent of an entry may be a symlink: following one could write outside the rootfs. */
  private fun ensureParent(relative: String) {
    val parts = relative.split('/')
    var path = ""
    for (part in parts.dropLast(1)) {
      path = if (path.isEmpty()) part else "$path/$part"
      if (path in checkedDirs) continue
      val dir = File(target, path)
      if (fs.isSymlink(dir.path)) throw IOException("entry $relative is under the symlink $path")
      if (!dir.isDirectory && !dir.mkdir()) throw IOException("cannot create directory $path")
      checkedDirs += path
    }
  }

  companion object {
    /** The entry's path inside the rootfs, or null for the root itself and OCI whiteouts. */
    internal fun relativePath(path: String): String? {
      val parts = path.split('/').filter { it.isNotEmpty() && it != "." }
      if (parts.isEmpty()) return null
      if (parts.any { it == ".." }) throw IOException("tar entry leaves the rootfs: $path")
      if (parts.last().startsWith(".wh.")) return null
      return parts.joinToString("/")
    }
  }
}
