package tech.nouverse.nouride.android.download

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File
import java.io.IOException
import java.io.OutputStream

/**
 * The phone's Downloads folder, where a file from the dashboard is kept: the folder every Files
 * app opens, and the one a person finds a download in.
 *
 * The app writes there itself, never through `DownloadManager`: that runs in another process, which may not speak plain
 * HTTP to the loopback (`network_security_config.xml` allows it to this app only), cannot reach the dashboard's
 * session, and cannot read a `blob:` at all. From Android 10 the file goes in through `MediaStore.Downloads`, which
 * needs no permission; before it, Android asks the person once for storage (`needsPermission`).
 *
 * A name is never written over: [Downloads.free] against the names this app can see there, and `MediaStore` numbers
 * any it cannot see itself. What was written is said back, as it may be another name than the one asked for.
 */
class DownloadsFolder(private val context: Context) {
  /** What became of a save. */
  sealed class Saved {
    data class Done(val name: String) : Saved()

    /** The phone is full: said on its own, as the person can fix it. */
    object NoRoom : Saved()

    object Failed : Saved()
  }

  private val folder: File get() = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

  /** Before Android 10 a file in Downloads needs the storage permission, asked once. */
  fun needsPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
    context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED

  /** Bytes free where Downloads is. */
  fun freeBytes(): Long = try {
    StatFs(Environment.getExternalStorageDirectory().path).availableBytes
  } catch (e: IllegalArgumentException) {
    Long.MAX_VALUE
  }

  /** Whether [bytes] more fit, with room left for the phone itself. */
  fun fits(bytes: Long): Boolean = bytes < 0 || bytes + HEADROOM <= freeBytes()

  /** [write] streamed into a new file named [name] (or its next free copy) in Downloads; a part written is removed. */
  fun save(name: String, write: (OutputStream) -> Unit): Saved {
    val chosen = Downloads.free(name) { File(folder, it).exists() }
    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) viaMediaStore(chosen, write) else viaFile(chosen, write)
    } catch (e: IOException) {
      if (freeBytes() < HEADROOM) Saved.NoRoom else Saved.Failed
    } catch (e: SecurityException) {
      Saved.Failed
    }
  }

  private fun viaMediaStore(name: String, write: (OutputStream) -> Unit): Saved {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
      put(MediaStore.Downloads.DISPLAY_NAME, name)
      put(MediaStore.Downloads.MIME_TYPE, mimeOf(name))
      put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
      put(MediaStore.Downloads.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IOException("no entry in Downloads")
    try {
      (resolver.openOutputStream(uri) ?: throw IOException("Downloads would not open")).use(write)
      resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
    } catch (e: Exception) {
      resolver.delete(uri, null, null)
      throw e
    }
    val written = resolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)?.use { cursor ->
      if (cursor.moveToFirst()) cursor.getString(0) else null
    }
    return Saved.Done(written ?: name)
  }

  private fun viaFile(name: String, write: (OutputStream) -> Unit): Saved {
    folder.mkdirs()
    val file = File(folder, name)
    try {
      file.outputStream().use(write)
    } catch (e: Exception) {
      file.delete()
      throw e
    }
    MediaScannerConnection.scanFile(context, arrayOf(file.path), null, null)
    return Saved.Done(name)
  }

  companion object {
    /** Left free for the phone itself: a download that would fill it is said not to fit. */
    const val HEADROOM = 64L * 1024 * 1024

    /**
     * The type `MediaStore` is told, from the name's extension: told another, it may add an extension of its own
     * (`notes.md` saved as `notes.md.txt`).
     */
    fun mimeOf(name: String): String {
      val ext = name.substringAfterLast('.', "").lowercase()
      return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }
  }
}
