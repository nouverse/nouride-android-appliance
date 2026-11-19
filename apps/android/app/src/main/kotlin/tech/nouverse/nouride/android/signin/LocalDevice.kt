package tech.nouverse.nouride.android.signin

import android.content.Context
import android.os.Build
import android.provider.Settings
import org.json.JSONObject
import tech.nouverse.nouride.android.Layout
import tech.nouverse.nouride.android.Nouride
import tech.nouverse.nouride.android.server.ServerService
import tech.nouverse.nouride.android.update.NourideUpdate
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * [DeviceSignIn]'s calls on the phone's own Nouride, and the token in the app's private preferences. Blocking: off the
 * main thread. The device token and the control token are never logged or shown, and sent only to this phone's
 * Nouride.
 */
class LocalDevice(private val context: Context) : DeviceSignIn.Effects {
  private val prefs = context.getSharedPreferences(ServerService.PREFS, Context.MODE_PRIVATE)
  private val controlToken = File(Layout(context).rootfs, NourideUpdate.TOKEN_PATH)

  /** The kept token, or null. */
  fun kept(): String? = prefs.getString(PREF_DEVICE_TOKEN, null)?.takeIf { it.isNotBlank() }

  /** One round, the session cookie when it signed in. */
  fun signIn(): DeviceSignIn.Outcome = DeviceSignIn.round(kept(), this)

  override fun trade(token: String): DeviceSignIn.Answer = post("/auth/device", JSONObject().put("token", token), null)

  override fun mint(): DeviceSignIn.Answer {
    val control = try {
      controlToken.readText().trim()
    } catch (e: IOException) {
      ""
    }
    if (control.isEmpty()) return DeviceSignIn.Answer(0)
    return post("/auth/devices", JSONObject().put("name", DeviceSignIn.label(deviceName())), control)
  }

  /**
   * What the phone is called: its name in About phone ("Redmi Note 12"), which `Build.MODEL` is not on every phone
   * (`23021RAA2Y` on that one); the model when the phone has no name, as before Android 7.1.
   */
  private fun deviceName(): String {
    val named = if (Build.VERSION.SDK_INT >= 25) Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) else null
    return named?.takeIf { it.isNotBlank() } ?: Build.MODEL.orEmpty()
  }

  override fun keep(token: String) {
    prefs.edit().putString(PREF_DEVICE_TOKEN, token).commit()
  }

  override fun waitFor(ms: Long) = Thread.sleep(ms)

  private fun post(path: String, body: JSONObject, control: String?): DeviceSignIn.Answer {
    val connection = try {
      URL("${Nouride.ORIGIN}/api/control$path").openConnection() as HttpURLConnection
    } catch (e: IOException) {
      return DeviceSignIn.Answer(0)
    }
    return try {
      connection.requestMethod = "POST"
      connection.connectTimeout = 5_000
      connection.readTimeout = 10_000
      connection.doOutput = true
      connection.setRequestProperty("Content-Type", "application/json")
      connection.setRequestProperty("Accept", "application/json")
      if (control != null) connection.setRequestProperty("x-nouride-token", control)
      connection.outputStream.use { it.write(body.toString().toByteArray()) }
      val code = connection.responseCode
      val text = (if (code < 400) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
      val cookies = connection.headerFields.entries.filter { it.key.equals("set-cookie", true) }.flatMap { it.value }
      val value = try {
        JSONObject(text).optString("value").takeIf { it.isNotEmpty() }
      } catch (e: Exception) {
        null
      }
      DeviceSignIn.Answer(code, connection.getHeaderField("retry-after"), DeviceSignIn.sessionCookie(cookies), value)
    } catch (e: IOException) {
      DeviceSignIn.Answer(0)
    } finally {
      connection.disconnect()
    }
  }

  companion object {
    const val PREF_DEVICE_TOKEN = "device_token"
  }
}
