package tech.nouverse.nouride.android.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import tech.nouverse.nouride.android.R
import tech.nouverse.nouride.android.server.ServerState
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Updates of Nouride Home itself, from the repository's GitHub Releases (`UpdateFeed`): the app is sideloaded, so there
 * is no store to update it. The check runs when the app opens and twice a day from the service; a release found is kept
 * here until it is installed. Downloading checks the APK against the release's sha256, and Android's installer asks the
 * person once, as it does for every app from outside a store. Every release is signed with the same key, so the update
 * keeps Debian, Nouride and its agents; `BootReceiver` starts Nouride again once the new version is in.
 * Nouride inside updates itself, from its own dashboard: this is only the app around it.
 */
object Updates {
  private const val PREFS = "updates"
  private const val VERSION = "version"
  private const val APK = "apk"
  private const val SHA = "sha"
  private const val CHECKED_AT = "checked_at"
  private const val CHECKED_FOR = "checked_for"
  private const val OFFERED = "offered"
  private const val FAILED = "failed"

  /** How old the last answer may be when the app opens before the feed is asked again. */
  const val ON_OPEN_MS = 6 * 60 * 60 * 1000L

  /**
   * While the first start sets Nouride up, an update waits: replacing the app stops the setup halfway, and an installer
   * cut off inside apt can leave it unable to finish. It waits from the moment the person presses Start on a phone
   * where Nouride is not installed yet, not from the service's first report, which came after the big card once on the
   * phone. A setup that failed does not wait: the update may be what fixes it. Every later start is quick and resumes.
   */
  fun waitsFor(phase: ServerState.Phase, startPressed: Boolean, installed: () -> Boolean): Boolean = when (phase) {
    ServerState.Phase.EXTRACTING, ServerState.Phase.INSTALLING -> true
    ServerState.Phase.FAILED -> false
    else -> startPressed && !installed()
  }

  fun installedVersion(context: Context): String =
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""

  /** The release waiting to be installed, from the last check; null when there is none newer than this app. */
  fun waiting(context: Context): Release? {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val version = prefs.getString(VERSION, null) ?: return null
    if (!UpdateFeed.newer(version, installedVersion(context))) return null
    return Release(version, prefs.getString(APK, null) ?: return null, prefs.getString(SHA, null) ?: return null)
  }

  /**
   * Asks the feed when the last answer is older than [maxAgeMs], and returns the release waiting. Blocking: off the main
   * thread. A failed check keeps the last answer: no network is not "no update".
   */
  fun check(context: Context, maxAgeMs: Long): Release? {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val installed = installedVersion(context)
    if (!UpdateFeed.due(System.currentTimeMillis(), prefs.getLong(CHECKED_AT, 0), maxAgeMs, prefs.getString(CHECKED_FOR, null), installed)) {
      return waiting(context)
    }
    val text = try {
      get(context.getString(R.string.update_feed), context)
    } catch (e: IOException) {
      return waiting(context)
    }
    val release = UpdateFeed.parse(text)
    prefs.edit().apply {
      putLong(CHECKED_AT, System.currentTimeMillis()).putString(CHECKED_FOR, installed)
      if (release != null) putString(VERSION, release.version).putString(APK, release.apkUrl).putString(SHA, release.sha256Url)
    }.apply()
    return waiting(context)
  }

  /** Whether the person has been asked about [version] already: the big card shows once a release, then the small one. */
  fun offered(context: Context, version: String): Boolean =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(OFFERED, null) == version

  fun markOffered(context: Context, version: String) =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(OFFERED, version).apply()

  /** Why the last install did not go in, set by `UpdateReceiver`; cleared when the next one starts. */
  fun failed(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(FAILED, false)

  fun setFailed(context: Context, failed: Boolean) =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(FAILED, failed).apply()

  /** Downloads the release's APK and checks it against its sha256. Blocking. [progress] gets 0 to 100. */
  fun download(context: Context, release: Release, progress: (Int) -> Unit): File {
    val name = "nouride-home-${release.version}.apk"
    val expected = UpdateFeed.expectedSha256(get(release.sha256Url, context), name)
      ?: throw IOException("the release's sha256 file does not name $name")
    val dir = File(context.cacheDir, "updates").apply { mkdirs() }
    // Downloaded and checked before, and not installed since: straight to the installer (`Downloaded`).
    Downloaded.keep(dir, name, expected)?.let {
      progress(100)
      return it
    }
    val file = File(dir, name)
    val digest = MessageDigest.getInstance("SHA-256")
    val connection = open(release.apkUrl, context)
    try {
      if (connection.responseCode != 200) throw IOException("HTTP ${connection.responseCode} for the APK")
      val total = connection.contentLengthLong
      var done = 0L
      connection.inputStream.use { input ->
        file.outputStream().use { output ->
          val buffer = ByteArray(1 shl 16)
          var last = -1
          while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            output.write(buffer, 0, n)
            digest.update(buffer, 0, n)
            done += n
            val percent = if (total > 0) (done * 100 / total).toInt() else 0
            if (percent != last) progress(percent).also { last = percent }
          }
        }
      }
    } finally {
      connection.disconnect()
    }
    val got = digest.digest().joinToString("") { "%02x".format(it) }
    if (got != expected) {
      file.delete()
      throw IOException("the APK's sha256 is $got, the release says $expected")
    }
    return file
  }

  /** Whether Android lets this app install its update; from Android 8 the person allows it once in Settings. */
  fun canInstall(context: Context): Boolean =
    Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

  /** The Settings page where the person allows it. */
  fun allowInstallIntent(context: Context): Intent =
    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

  /** Hands the APK to Android's installer, which asks the person and then replaces this app. */
  fun install(context: Context, apk: File) {
    setFailed(context, false)
    val installer = context.packageManager.packageInstaller
    val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
    params.setAppPackageName(context.packageName)
    val id = installer.createSession(params)
    installer.openSession(id).use { session ->
      session.openWrite("nouride-home.apk", 0, apk.length()).use { output ->
        apk.inputStream().use { it.copyTo(output, 1 shl 16) }
        session.fsync(output)
      }
      val intent = Intent(context, UpdateReceiver::class.java).setAction(UpdateReceiver.ACTION)
      // Android fills in the status: the intent has to stay mutable for it from Android 12.
      val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
      session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
    }
  }

  private fun open(url: String, context: Context): HttpURLConnection =
    (URL(url).openConnection() as HttpURLConnection).apply {
      connectTimeout = 15_000
      readTimeout = 60_000
      instanceFollowRedirects = true
      setRequestProperty("User-Agent", "NourideHome/${installedVersion(context)}")
      setRequestProperty("Accept", "application/vnd.github+json, */*")
    }

  private fun get(url: String, context: Context): String {
    val connection = open(url, context)
    try {
      if (connection.responseCode != 200) throw IOException("HTTP ${connection.responseCode} for $url")
      return connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
      connection.disconnect()
    }
  }
}
