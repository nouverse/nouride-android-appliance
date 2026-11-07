package tech.nouverse.nouride.android.signin

import android.content.Context
import android.text.Html
import android.webkit.CookieManager
import tech.nouverse.nouride.android.BuildConfig
import tech.nouverse.nouride.android.Nouride
import tech.nouverse.nouride.android.R
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import kotlin.concurrent.thread

/**
 * Listens on a provider's loopback redirect for the one sign-in the app just opened in the browser (`LoopbackSignIn`),
 * hands its code to the LLM gateway with the dashboard's session, and tells the browser how it went with a way back to
 * the app. Bound to 127.0.0.1 only, and only while a sign-in is waiting: never a port open on the Wi-Fi.
 */
object CallbackCatcher {
  private val lock = Any()
  private var socket: ServerSocket? = null

  fun arm(context: Context, expected: LoopbackSignIn.Expected) {
    val app = context.applicationContext
    val server = synchronized(lock) {
      socket?.close()
      try {
        ServerSocket(expected.route.port, 8, InetAddress.getByName("127.0.0.1")).also {
          it.soTimeout = LoopbackSignIn.WINDOW_MS.toInt()
          socket = it
        }
      } catch (e: IOException) {
        // The port is taken: the person still has the dashboard's paste box.
        return
      }
    }
    thread(name = "sign-in-callback", isDaemon = true) {
      try {
        while (!server.isClosed) {
          val done = server.accept().use { answer(app, expected, it) }
          if (done) break
        }
      } catch (e: SocketTimeoutException) {
        // Nobody came back within the window.
      } catch (e: IOException) {
        // Closed by the next sign-in.
      } finally {
        synchronized(lock) { if (socket === server) socket = null }
        server.close()
      }
    }
  }

  /** True once the sign-in is settled either way: one code, used once. */
  private fun answer(context: Context, expected: LoopbackSignIn.Expected, client: Socket): Boolean {
    client.soTimeout = 10_000
    val target = client.getInputStream().bufferedReader().readLine()?.split(' ')?.getOrNull(1) ?: return false
    val callback = LoopbackSignIn.callback(expected.route, target)
    if (callback == null) {
      respond(client, 404, "")
      return false
    }
    val ok = LoopbackSignIn.accepts(expected, callback, System.currentTimeMillis()) && addAccount(expected.route, callback)
    respond(client, 200, page(context, ok))
    return true
  }

  private fun addAccount(route: LoopbackSignIn.Route, callback: LoopbackSignIn.Callback): Boolean {
    val cookie = CookieManager.getInstance().getCookie(Nouride.ORIGIN) ?: return false
    val connection = URL(LoopbackSignIn.addAccountUrl(route)).openConnection() as HttpURLConnection
    return try {
      connection.requestMethod = "POST"
      connection.connectTimeout = 5_000
      connection.readTimeout = 60_000
      connection.doOutput = true
      connection.setRequestProperty("content-type", "application/json")
      connection.setRequestProperty("cookie", cookie)
      connection.outputStream.use { it.write(LoopbackSignIn.addAccountBody(callback).toByteArray()) }
      val code = connection.responseCode
      val body = (if (code >= 400) connection.errorStream else connection.inputStream)?.bufferedReader()?.use { it.readText() } ?: ""
      code == 200 && body.contains("\"success\":true")
    } catch (e: IOException) {
      false
    } finally {
      connection.disconnect()
    }
  }

  private fun page(context: Context, ok: Boolean): String {
    val title = context.getString(if (ok) R.string.signin_done_title else R.string.signin_failed_title)
    val body = context.getString(if (ok) R.string.signin_done_body else R.string.signin_failed_body)
    val back = context.getString(R.string.signin_back)
    val esc = { text: String -> Html.escapeHtml(text) }
    return """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${esc(title)}</title><style>body{font-family:sans-serif;background:#F5F8FF;color:#1E2A3B;margin:0;padding:48px 24px;text-align:center}
h1{font-size:24px}p{color:#5B6B82;font-size:17px}a{display:block;margin:32px auto 0;max-width:360px;padding:16px;border-radius:32px;
background:#0084FC;color:#fff;font-weight:bold;font-size:18px;text-decoration:none}
@media (prefers-color-scheme:dark){body{background:#0F1724;color:#E6ECF5}p{color:#9AA8BD}a{background:#3A9DFF}}</style></head>
<body><h1>${esc(title)}</h1><p>${esc(body)}</p><a href="$BACK">${esc(back)}</a>
<script>setTimeout(function(){location.href="$BACK"},600)</script></body></html>"""
  }

  private fun respond(client: Socket, status: Int, html: String) {
    val bytes = html.toByteArray()
    val head = "HTTP/1.1 $status ${if (status == 200) "OK" else "Not Found"}\r\n" +
      "Content-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
    client.getOutputStream().apply {
      write(head.toByteArray())
      write(bytes)
      flush()
    }
  }

  /** Brings the dashboard back where it was, and it asks for its accounts again (`DashboardActivity`). */
  const val BACK = "${BuildConfig.LINK_SCHEME}://dashboard"
}
