package tech.nouverse.nouride.android.install

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants

object AndroidFileOps : FileOps {
  override fun symlink(target: String, path: String) = Os.symlink(target, path)

  override fun chmod(path: String, mode: Int) = Os.chmod(path, mode)

  override fun isSymlink(path: String): Boolean = try {
    OsConstants.S_ISLNK(Os.lstat(path).st_mode)
  } catch (e: ErrnoException) {
    false
  }
}
