package tech.nouverse.nouride.android

/** What the app runs inside Debian. */
object Nouride {
  /** Nouride's default port, as on every other install. */
  const val PORT = 18254
  const val INSTALL_URL = "https://get.nouride.com/install.sh"

  /** As root in Debian: the packages the minimal image lacks, then the official installer. */
  val INSTALL = listOf(
    "/bin/sh", "-c",
    """
    set -e
    export DEBIAN_FRONTEND=noninteractive
    apt-get update
    apt-get install -y curl ca-certificates procps
    curl -fsSL $INSTALL_URL | sh -s -- --host 0.0.0.0 --port $PORT
    """.trimIndent(),
  )

  /** The start command the installer prints when there is no systemd, as the account it made. */
  val START = listOf("/bin/su", "-s", "/bin/sh", "nouride", "-c", "cd /srv/nouride && exec ./nouride start")

  /** What the daemon logs at the end of every boot. */
  const val READY_LINE = "nouride ready"

  const val HEALTH_URL = "http://127.0.0.1:$PORT/health"
  const val DASHBOARD_URL = "http://127.0.0.1:$PORT/dashboard/"
}
