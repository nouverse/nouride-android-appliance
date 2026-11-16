package tech.nouverse.nouride.android.update

import org.json.JSONException
import org.json.JSONObject

/** A release of Nouride Home the phone can update to: its version, its APK and the sha256 the APK must have. */
data class Release(val version: String, val apkUrl: String, val sha256Url: String)

/**
 * What the update check reads: the newest release on the repository's GitHub Releases (`releases/latest`, which leaves
 * out drafts and pre-releases), as the release workflow publishes it: a tag `vX.Y.Z` with `nouride-home-X.Y.Z.apk` and
 * `nouride-home-X.Y.Z.apk.sha256` attached. Pure, so every shape a feed can take is tested without a network.
 */
object UpdateFeed {
  /** The release a feed's JSON names, or null when it names none this app can install. */
  fun parse(json: String): Release? {
    val root = try {
      JSONObject(json)
    } catch (e: JSONException) {
      return null
    }
    if (root.optBoolean("draft") || root.optBoolean("prerelease")) return null
    val version = root.optString("tag_name").removePrefix("v")
    if (parseVersion(version) == null) return null
    val assets = root.optJSONArray("assets") ?: return null
    val urls = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
      .associate { it.optString("name") to it.optString("browser_download_url") }
    val apk = urls["nouride-home-$version.apk"]?.takeIf { it.startsWith("https://") || it.startsWith("http://127.0.0.1") } ?: return null
    val sha = urls["nouride-home-$version.apk.sha256"]?.takeIf { it.startsWith("https://") || it.startsWith("http://127.0.0.1") } ?: return null
    return Release(version, apk, sha)
  }

  /** `x.y.z` as three numbers; null for anything else, a build of no release (`0.0.0-dev`) included. */
  fun parseVersion(version: String): List<Int>? {
    val parts = version.removePrefix("v").split('.')
    if (parts.size != 3) return null
    return parts.map { part -> part.toIntOrNull()?.takeIf { it >= 0 && part.all(Char::isDigit) } ?: return null }
  }

  /**
   * Whether [candidate] is a later release than [installed]. Never for a build that is not a release: a developer's
   * build is not nagged to become the last release.
   */
  fun newer(candidate: String, installed: String): Boolean {
    val a = parseVersion(candidate) ?: return false
    val b = parseVersion(installed) ?: return false
    for (i in 0 until 3) if (a[i] != b[i]) return a[i] > b[i]
    return false
  }

  /** The sha256 a `sha256sum` line gives for [fileName], lower case; null when the text names no such file. */
  fun expectedSha256(text: String, fileName: String): String? =
    text.lines().map { it.trim() }.firstNotNullOfOrNull { line ->
      val match = Regex("^([0-9a-fA-F]{64})\\s+\\*?(\\S+)$").find(line) ?: return@firstNotNullOfOrNull null
      if (match.groupValues[2] == fileName) match.groupValues[1].lowercase() else null
    }

  /**
   * Whether the feed is asked again: the last answer is older than [maxAgeMs], or it was given to another version of
   * this app. An answer kept from before an update is about the old version: the phone updated by hand (or from a test
   * feed) waited out the rest of six hours before it looked for anything newer than itself.
   */
  fun due(nowMs: Long, checkedAtMs: Long, maxAgeMs: Long, checkedFor: String?, installed: String): Boolean =
    checkedFor != installed || nowMs - checkedAtMs >= maxAgeMs
}
