package tech.nouverse.nouride.android

import tech.nouverse.nouride.android.server.SetupProgress
import tech.nouverse.nouride.android.server.Step

/** What the app runs inside Debian. */
object Nouride {
  /** Nouride's default port, as on every other install; a developer's build takes the next one (`build.gradle.kts`). */
  const val PORT = BuildConfig.NOURIDE_PORT

  /**
   * Where the Router edition's LLM gateway listens, on the phone's loopback. Nouride's default (`[nougate] port`) for a
   * release; a developer's build moves it too, or its Nouride would reach the release's gateway and its accounts.
   */
  const val GATEWAY_PORT = BuildConfig.GATEWAY_PORT
  const val DEFAULT_GATEWAY_PORT = 18256
  const val INSTALL_URL = "https://get.nouride.com/install.sh"

  /**
   * As root in Debian: the packages the minimal image lacks, the account Nouride runs as (`serviceAccount`), then the
   * official installer. Each part echoes its marker first, which moves the progress bar to that step (`SetupProgress`).
   * The Router edition: the free build with the LLM gateway, so a person can sign in with an account they already pay
   * for instead of finding an API key. A gateway port other than Nouride's default goes into its config before the
   * setup wizard reads it, owned by the account Nouride runs as, which writes that file later.
   */
  val INSTALL = listOf("/bin/sh", "-c", installScript(GATEWAY_PORT))

  internal fun installScript(gatewayPort: Int): String = """
    set -e
    export DEBIAN_FRONTEND=noninteractive
    echo '${SetupProgress.marker(Step.PACKAGES)}'
    apt-get update
    echo '${SetupProgress.marker(Step.TOOLS)}'
    apt-get install -y curl ca-certificates procps
    echo '${SetupProgress.marker(Step.NOURIDE)}'
    ${serviceAccount()}
    curl -fsSL $INSTALL_URL | sh -s -- --variant router --host 0.0.0.0 --port $PORT
    """.trimIndent() + (if (gatewayPort == DEFAULT_GATEWAY_PORT) "" else "\n" + gatewayConfig(gatewayPort))

  /** The folder Nouride keeps everything in, in Debian (`backup/NourideBackup` zips it). */
  const val DATA_DIR = "/srv/nouride/.nouride"

  private const val HOME = DATA_DIR

  /**
   * The account Nouride runs as, made here before the installer, which then skips its own `useradd` ("user nouride
   * already exists"). shadow's `useradd` locks `/etc/passwd` with a hard link, which proot only emulates
   * (`--link2symlink`); on a Realme with Android 9 it stopped the first start ("cannot lock /etc/passwd", issue #3),
   * likely that emulation failing on its kernel, not proven. So the four lines are appended as plain text: what
   * `useradd --system --no-create-home --home-dir <home> --shell /usr/sbin/nologin nouride` makes on Debian, a system
   * id free as a user's and as a group's (the highest from 999 down to 101, login.defs' defaults), a locked password
   * with no aging, and a group of its own with no members. An account already there is left as it is.
   */
  internal fun serviceAccount(etc: String = "/etc"): String =
    "if ! grep -q '^nouride:' $etc/passwd; then id=999; " +
      "while cut -d: -f3 $etc/passwd $etc/group | grep -qx \"\$id\"; do id=\$((id - 1)); done; " +
      "if [ \$id -lt 101 ]; then echo 'no free system id for the nouride account' >&2; exit 1; fi; " +
      "echo \"nouride:x:\$id:\" >> $etc/group; " +
      "echo 'nouride:!::' >> $etc/gshadow; " +
      "echo \"nouride:!:\$((\$(date +%s) / 86400))::::::\" >> $etc/shadow; " +
      "echo \"nouride:x:\$id:\$id::$HOME/home:/usr/sbin/nologin\" >> $etc/passwd; fi"

  private const val CONFIG = "$HOME/config.toml"

  /**
   * The gateway's port in Nouride's `config.toml`, whatever the installer left there. The Router edition's installer
   * writes that file itself (`[nougate]` with `in_process = true`), so writing it only when it is missing left the
   * debug build on the release's port, where it lost to the release beside it and restarted for ever. Missing, the file
   * is made, owned as Nouride's folder is; without `[nougate]`, the section is added; with it but no `port`, the port goes
   * under its heading. A port already there is kept, so running it again changes nothing.
   */
  internal fun gatewayConfig(port: Int, config: String = CONFIG, home: String = HOME): String =
    "if [ ! -e $config ]; then printf '[nougate]\\nport = %s\\n' $port > $config; " +
      "chown --reference=$home $config; chmod 640 $config; " +
      "elif ! grep -q '^\\[nougate\\]' $config; then printf '\\n[nougate]\\nport = %s\\n' $port >> $config; " +
      "elif ! sed -n '/^\\[nougate\\]/,/^\\[/p' $config | grep -q '^port *='; then " +
      "sed -i '/^\\[nougate\\]\$/a port = $port' $config; fi"

  /**
   * What the app tells Nouride about itself: that it starts Nouride again whenever it exits (`Supervisor`). Nouride's
   * own word for that (`service/supervisor.ts`); without it Nouride took itself for unsupervised and refused the
   * dashboard's Restart, and an update, as a stop: "nothing on this server would start it again".
   */
  const val SUPERVISOR_MODE = "NOURIDE_SUPERVISOR_MODE=nouride-home"

  /** The start command the installer prints when there is no systemd, as the account it made, saying who restarts it. */
  val START = listOf("/bin/su", "-s", "/bin/sh", "nouride", "-c", "cd /srv/nouride && $SUPERVISOR_MODE exec ./nouride start")

  /**
   * A consistent snapshot of Nouride's database, safe while it runs (`VACUUM INTO`), as the account it runs as, the
   * answer one JSON line naming the file (`Backup.snapshot`).
   */
  val DB_BACKUP = listOf("/bin/su", "-s", "/bin/sh", "nouride", "-c", "cd /srv/nouride && ./nouride db backup --json")

  /** What the daemon logs at the end of every boot. */
  const val READY_LINE = "nouride ready"

  const val HEALTH_URL = "http://127.0.0.1:$PORT/health"
  const val ORIGIN = "http://127.0.0.1:$PORT"
  const val DASHBOARD_URL = "http://127.0.0.1:$PORT/dashboard/"
}
