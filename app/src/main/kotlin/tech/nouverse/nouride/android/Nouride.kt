package tech.nouverse.nouride.android

/** What the app runs inside Debian. */
object Nouride {
  /** Not the default 18254: that port is the phone's Termux install of Nouride. */
  const val PORT = 18264
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
