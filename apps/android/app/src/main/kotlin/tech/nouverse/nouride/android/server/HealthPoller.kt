package tech.nouverse.nouride.android.server

import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** One answer of the daemon's /health, or why there was none. */
data class Health(
  val httpCode: Int,
  val status: String?,
  val version: String?,
  val uptimeSeconds: Long?,
  val issues: List<String>,
  val error: String?,
) {
  /** The daemon answers 200 with `ok` only; `starting`, `degraded` and `shutting_down` are 503. */
  val ok: Boolean get() = httpCode == 200 && status == "ok"

  fun summary(): String = when {
    error != null -> "unreachable: $error"
    status == null -> "HTTP $httpCode"
    else -> buildString {
      append(status)
      if (version != null) append(", version $version")
      if (uptimeSeconds != null) append(", up ${uptimeSeconds}s")
      if (issues.isNotEmpty()) append(", issues: ${issues.joinToString("; ")}")
    }
  }

  companion object {
    fun unreachable(error: String) = Health(0, null, null, null, emptyList(), error)

    fun parse(httpCode: Int, body: String): Health {
      val json = try {
        JSONObject(body)
      } catch (e: JSONException) {
        return Health(httpCode, null, null, null, emptyList(), "not JSON: ${body.take(80)}")
      }
      val issues = json.optJSONArray("issues")
      return Health(
        httpCode = httpCode,
        status = json.optString("status").ifEmpty { null },
        version = json.optString("version").ifEmpty { null },
        uptimeSeconds = if (json.has("uptime")) json.optLong("uptime") else null,
        issues = (0 until (issues?.length() ?: 0)).map { issues!!.optString(it) },
        error = null,
      )
    }
  }
}

/** Asks [url] every [intervalSeconds] and hands each [Health] to [onResult]. */
class HealthPoller(
  private val url: String,
  private val intervalSeconds: Long,
  private val onResult: (Health) -> Unit,
) {
  private var executor: ScheduledExecutorService? = null

  fun start() {
    if (executor != null) return
    executor = Executors.newSingleThreadScheduledExecutor { Thread(it, "health-poller").apply { isDaemon = true } }
      .also { it.scheduleWithFixedDelay({ onResult(check()) }, 0, intervalSeconds, TimeUnit.SECONDS) }
  }

  /** One extra check now, as soon as the daemon says it is ready, rather than up to a whole interval later. */
  fun checkNow() {
    executor?.execute { onResult(check()) }
  }

  fun stop() {
    executor?.shutdownNow()
    executor = null
  }

  fun check(): Health {
    val connection = try {
      URL(url).openConnection() as HttpURLConnection
    } catch (e: IOException) {
      return Health.unreachable(e.toString())
    }
    return try {
      connection.connectTimeout = 5_000
      connection.readTimeout = 10_000
      val code = connection.responseCode
      val stream = if (code >= 400) connection.errorStream else connection.inputStream
      Health.parse(code, stream?.bufferedReader()?.use { it.readText() } ?: "")
    } catch (e: IOException) {
      Health.unreachable(e.toString())
    } finally {
      connection.disconnect()
    }
  }
}
