package tech.nouverse.nouride.android.ui

import android.Manifest
import android.app.Activity
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import tech.nouverse.nouride.android.R
import tech.nouverse.nouride.android.download.Downloads
import tech.nouverse.nouride.android.download.DownloadsFolder
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * The dashboard's downloads, kept in the phone's Downloads with a line that says so (`spec/downloads.md`).
 *
 * The dashboard downloads by clicking a link with a `download` name (`res/raw/dashboard_downloads.js` catches it): a
 * file on Nouride is fetched here with the dashboard's session, from `CookieManager`; a `blob:` the page made is read in
 * the page and handed over in pieces (`Bridge`). A download that reaches the WebView another way (a page the server
 * answers with `attachment`) comes through `setDownloadListener` and goes the same two ways. Only Nouride's own
 * (`Downloads.source`): another machine's address goes to the browser, so the session never leaves for it.
 */
internal class DashboardDownloads(
  private val activity: Activity,
  private val web: WebView,
  /** The Nouride the screen shows, asked at each download: the place may change while the screen is open. */
  private val origin: () -> String,
  private val openOutside: (String) -> Unit,
) {
  private val folder = DownloadsFolder(activity)
  private val script: String by lazy { activity.resources.openRawResource(R.raw.dashboard_downloads).bufferedReader().use { it.readText() } }

  /** Blobs being handed over: a file each in the app's cache until the last piece is in. */
  private val incoming = ConcurrentHashMap<String, Incoming>()

  /** A download waiting for Android's storage permission (before Android 10 only). */
  private var waiting: (() -> Unit)? = null

  private class Incoming(val name: String, val file: File)

  /** The WebView's, read here: a WebView may only be asked on the main thread, and a download runs on its own. */
  private lateinit var userAgent: String

  /** After the WebView's user agent is set. */
  fun attach() {
    userAgent = web.settings.userAgentString
    web.addJavascriptInterface(Bridge(), BRIDGE)
    web.setDownloadListener { url, _, disposition, _, _ -> fromListener(url, disposition) }
  }

  /** On every page of the dashboard, as soon as it is there. */
  fun inject() {
    web.evaluateJavascript(script, null)
  }

  /** Android's answer to the storage permission. */
  fun permissionAnswered(granted: Boolean) {
    val next = waiting
    waiting = null
    if (granted) next?.invoke() else say(R.string.download_needs_permission)
  }

  private fun fromListener(url: String, disposition: String?) {
    when (Downloads.source(url, origin())) {
      Downloads.Source.SAVE ->
        if (url.startsWith("blob:", ignoreCase = true)) {
          web.evaluateJavascript("window.__nourideHomeDownloads && window.__nourideHomeDownloads(${quoted(url)}, \"\")", null)
        } else {
          fetch(url, null, disposition)
        }
      Downloads.Source.OUTSIDE -> openOutside(url)
      Downloads.Source.REFUSED -> Unit
    }
  }

  /** A file on Nouride, fetched with the dashboard's session and streamed into Downloads. */
  private fun fetch(url: String, given: String?, disposition: String?) {
    when (Downloads.source(url, origin())) {
      Downloads.Source.SAVE -> Unit
      Downloads.Source.OUTSIDE -> return activity.runOnUiThread { openOutside(url) }
      Downloads.Source.REFUSED -> return
    }
    askFirst {
      val shown = Downloads.name(given, disposition, url)
      say(R.string.download_started, shown)
      thread(name = "dashboard-download") {
        val saved = try {
          get(url, given, disposition)
        } catch (e: IOException) {
          DownloadsFolder.Saved.Failed
        }
        report(saved, shown)
      }
    }
  }

  private fun get(url: String, given: String?, disposition: String?): DownloadsFolder.Saved {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
      connection.connectTimeout = 10_000
      connection.readTimeout = 60_000
      CookieManager.getInstance().getCookie(url)?.let { connection.setRequestProperty("Cookie", it) }
      connection.setRequestProperty("User-Agent", userAgent)
      if (connection.responseCode != HttpURLConnection.HTTP_OK) return DownloadsFolder.Saved.Failed
      val length = connection.getHeaderField("Content-Length")?.toLongOrNull() ?: -1
      if (!folder.fits(length)) return DownloadsFolder.Saved.NoRoom
      val name = Downloads.name(given, connection.getHeaderField("Content-Disposition") ?: disposition, url)
      return connection.inputStream.use { input -> folder.save(name) { out -> input.copyTo(out) } }
    } finally {
      connection.disconnect()
    }
  }

  /** Before Android 10, the storage permission first; then [work], once. */
  private fun askFirst(work: () -> Unit) = activity.runOnUiThread {
    if (!folder.needsPermission()) return@runOnUiThread work()
    waiting = work
    activity.requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), PERMISSION_REQUEST)
  }

  private fun report(saved: DownloadsFolder.Saved, shown: String) = when (saved) {
    is DownloadsFolder.Saved.Done -> say(R.string.download_saved, saved.name)
    DownloadsFolder.Saved.NoRoom -> say(R.string.download_no_room, shown)
    DownloadsFolder.Saved.Failed -> say(R.string.download_failed, shown)
  }

  private fun say(text: Int, vararg args: Any) = activity.runOnUiThread {
    if (activity.isDestroyed) return@runOnUiThread
    Toast.makeText(activity, activity.getString(text, *args), Toast.LENGTH_LONG).show()
  }

  /**
   * What the page's script calls, on the WebView's own thread for it. Every page in the WebView could call it, so a
   * blob is taken only when it is the dashboard's own (`Downloads.source`), and nothing is ever read back.
   */
  private inner class Bridge {
    @JavascriptInterface
    fun fetch(url: String, name: String) = this@DashboardDownloads.fetch(url, name, null)

    /** A blob of [size] bytes is coming: its id, or "" when it is not taken. */
    @JavascriptInterface
    fun begin(url: String, given: String, size: Double): String {
      if (Downloads.source(url, origin()) != Downloads.Source.SAVE) return ""
      val name = Downloads.name(given, null, url)
      if (!folder.fits(size.toLong())) {
        say(R.string.download_no_room, name)
        return ""
      }
      val id = UUID.randomUUID().toString()
      val dir = File(activity.cacheDir, "downloads").apply { mkdirs() }
      incoming[id] = Incoming(name, File(dir, id).apply { delete() })
      say(R.string.download_started, name)
      return id
    }

    @JavascriptInterface
    fun piece(id: String, base64: String) {
      val each = incoming[id] ?: return
      try {
        each.file.appendBytes(Base64.decode(base64, Base64.DEFAULT))
      } catch (e: Exception) {
        incoming.remove(id)
        each.file.delete()
        report(DownloadsFolder.Saved.Failed, each.name)
      }
    }

    @JavascriptInterface
    fun end(id: String) {
      val each = incoming.remove(id) ?: return
      askFirst {
        thread(name = "dashboard-download") {
          val saved = try {
            each.file.inputStream().use { input -> folder.save(each.name) { out -> input.copyTo(out) } }
          } finally {
            each.file.delete()
          }
          report(saved, each.name)
        }
      }
    }

    @JavascriptInterface
    fun fail(id: String) {
      val each = incoming.remove(id) ?: return
      each.file.delete()
      report(DownloadsFolder.Saved.Failed, each.name)
    }

    /** A blob the page made before this screen could keep it, or let go of already. */
    @JavascriptInterface
    fun unread(given: String) = report(DownloadsFolder.Saved.Failed, Downloads.safe(given))
  }

  private fun quoted(text: String): String = org.json.JSONObject.quote(text)

  companion object {
    const val PERMISSION_REQUEST = 41
    private const val BRIDGE = "NourideHomeDownloads"
  }
}
