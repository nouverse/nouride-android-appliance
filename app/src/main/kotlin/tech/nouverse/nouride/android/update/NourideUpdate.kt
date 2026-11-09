package tech.nouverse.nouride.android.update

import org.json.JSONException
import org.json.JSONObject

/** Nouride's own download of a new version, as its update routes report it (`download`). */
data class NourideDownload(val status: Status, val received: Long, val total: Long?) {
  enum class Status { IDLE, DOWNLOADING, PAUSED, READY, FAILED }

  /** 0 to 100; 0 while the size is not known yet. */
  val percent: Int get() = if (total == null || total <= 0) 0 else (received * 100 / total).toInt().coerceIn(0, 100)
}

/** What Nouride says about its own version, as much of it as the Versions screen shows. */
data class NourideStatus(
  /** The version running. */
  val current: String?,
  /** The newer version it can take; null when it has the newest, or could not ask. */
  val ready: String?,
  /** Whether this install can update itself at all. */
  val canUpdate: Boolean,
  /** The release channel did not answer: "could not check", never "up to date". */
  val checkFailed: Boolean,
  val download: NourideDownload,
)

/** When the new version takes over, as Nouride's answer to the install says. */
enum class Restart { NOW, IDLE, JOBS }

/**
 * Nouride's update, read the way its dashboard reads it: `GET /api/control/update` for the version and whether a newer
 * one is out, `POST /update/download` and `/update/progress` to fetch it, `POST /update/install` to put it in. Nouride
 * checks the release's signature and sha256, swaps the binary and exits, and the app's `Supervisor` starts it again,
 * as it does after the dashboard's Update. Pure, so every answer it can give is tested without a daemon.
 */
object NourideUpdate {
  /**
   * The install's control token, under the rootfs: what `nouride` commands on the machine sign in with (an admin, the
   * actor `cli`). The app runs that install, so it reads the file the way the CLI does, and the Versions screen works
   * whether or not the dashboard has an account the app knows.
   */
  const val TOKEN_PATH = "srv/nouride/.nouride/data/.control-token"

  /** The answer to `GET /update`; null when it is not one. */
  fun status(json: String): NourideStatus? {
    val root = parse(json) ?: return null
    val install = root.optJSONObject("install")
    val check = root.optJSONObject("check")
    val current = check?.optString("current")?.ifBlank { null } ?: install?.optString("version")?.ifBlank { null }
    val newer = check?.optBoolean("newer") == true
    val plan = check?.optJSONObject("plan")
    return NourideStatus(
      current = current?.removePrefix("v"),
      ready = check?.optString("latest")?.ifBlank { null }?.removePrefix("v")?.takeIf { newer },
      canUpdate = root.optBoolean("supported") && plan?.optBoolean("can", true) != false,
      checkFailed = root.has("error") && check == null,
      download = download(root.optJSONObject("download")),
    )
  }

  /** `download` from the answer to `/update/download` or `/update/progress`; null when the answer has none. */
  fun download(json: String): NourideDownload? = parse(json)?.optJSONObject("download")?.let(::download)

  /** When the new version takes over, from the answer to `/update/install`: right away when it does not say. */
  fun restart(json: String): Restart = when (parse(json)?.optString("restart")) {
    "idle" -> Restart.IDLE
    "jobs" -> Restart.JOBS
    else -> Restart.NOW
  }

  private fun download(json: JSONObject?): NourideDownload {
    val status = when (json?.optString("status")) {
      "downloading" -> NourideDownload.Status.DOWNLOADING
      "paused" -> NourideDownload.Status.PAUSED
      "ready" -> NourideDownload.Status.READY
      "failed" -> NourideDownload.Status.FAILED
      else -> NourideDownload.Status.IDLE
    }
    val total = json?.takeIf { it.has("total") && !it.isNull("total") }?.optLong("total")
    return NourideDownload(status, json?.optLong("received") ?: 0, total)
  }

  private fun parse(json: String): JSONObject? = try {
    JSONObject(json)
  } catch (e: JSONException) {
    null
  }
}
