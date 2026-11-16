package tech.nouverse.nouride.android.update

import android.content.Context
import tech.nouverse.nouride.android.Layout
import tech.nouverse.nouride.android.Nouride
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Nouride's update routes on the phone's own loopback (`NourideUpdate` says what they answer). Blocking: off the main
 * thread. The token is read for each call and sent only to this phone's Nouride; it is never logged or shown.
 */
object NourideUpdates {
  /** Whether Nouride is updated, and to what. [force] asks the release channel now, past Nouride's few minutes of cache. */
  fun status(context: Context, force: Boolean): NourideStatus =
    NourideUpdate.status(call(context, "GET", if (force) "/update?force=1" else "/update"))
      ?: throw IOException("Nouride's update answer could not be read")

  /** Starts the download, or picks it up where it stopped. Returns at once; [progress] follows it. */
  fun download(context: Context): NourideDownload =
    NourideUpdate.download(call(context, "POST", "/update/download")) ?: throw IOException("no download in Nouride's answer")

  fun progress(context: Context): NourideDownload =
    NourideUpdate.download(call(context, "GET", "/update/progress")) ?: throw IOException("no download in Nouride's answer")

  /** Stops the download, keeping what it has: [download] then goes on from there, on a new connection. */
  fun pause(context: Context) {
    call(context, "POST", "/update/pause")
  }

  /** Puts the downloaded version in. Nouride restarts on its own, when [Restart] says. */
  fun install(context: Context): Restart = NourideUpdate.restart(call(context, "POST", "/update/install"))

  private fun call(context: Context, method: String, path: String): String {
    val token = File(Layout(context).rootfs, NourideUpdate.TOKEN_PATH).readText().trim()
    if (token.isEmpty()) throw IOException("Nouride's control token is empty")
    val connection = URL("${Nouride.ORIGIN}/api/control$path").openConnection() as HttpURLConnection
    try {
      connection.requestMethod = method
      connection.connectTimeout = 5_000
      // The check asks GitHub, and the install swaps the binary before it answers: either can take a while.
      connection.readTimeout = 120_000
      connection.setRequestProperty("x-nouride-token", token)
      connection.setRequestProperty("Accept", "application/json")
      if (method == "POST") {
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write("{}".toByteArray()) }
      }
      val code = connection.responseCode
      val body = (if (code < 400) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
      if (code !in 200..299) throw IOException("HTTP $code from Nouride's $method $path")
      return body
    } finally {
      connection.disconnect()
    }
  }
}
