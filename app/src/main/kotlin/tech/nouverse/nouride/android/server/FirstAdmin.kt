package tech.nouverse.nouride.android.server

import java.security.SecureRandom

/**
 * The dashboard account the app makes for the person, so the dashboard on the phone opens signed in. A person who
 * never opened a terminal cannot dig a generated password out of a daemon log, which is the only other way in on a
 * fresh install.
 *
 * The app picks the password on the first start and keeps it in its own private storage. It reaches the daemon the
 * documented way, `INITIAL_ADMIN_USERNAME` and `INITIAL_ADMIN_PASSWORD` in the install's `.env` (`users/bootstrap.ts`
 * in Nouride), and those lines are taken out again once the daemon is ready: by then the account exists, and the file
 * should not keep a password it no longer needs. The app signs in with it each time it opens the dashboard; if the
 * person changed the password since, that sign-in fails and the dashboard shows its own login page.
 */
object FirstAdmin {
  const val USERNAME = "admin"
  private const val PREFIX = "INITIAL_ADMIN_"

  /** Hex, 24 characters: well above Nouride's password floor, and nothing a `.env` parser could misread. */
  fun newPassword(random: SecureRandom = SecureRandom()): String =
    ByteArray(12).also(random::nextBytes).joinToString("") { "%02x".format(it) }

  /** The `.env` with the account in it, in place of any `INITIAL_ADMIN_*` it had. */
  fun withAccount(env: String, password: String): String =
    withoutAccount(env).let { if (it.isEmpty() || it.endsWith("\n")) it else "$it\n" } +
      "${PREFIX}USERNAME=$USERNAME\n${PREFIX}PASSWORD=$password\n"

  /** The `.env` without any `INITIAL_ADMIN_*` line; every other line exactly as it was. */
  fun withoutAccount(env: String): String =
    env.split("\n").filterNot { it.trimStart().startsWith(PREFIX) }.joinToString("\n")

  /** The session cookie, `nouride_session=<token>`, from a sign-in's `Set-Cookie` headers; null when there is none. */
  fun sessionCookie(setCookie: List<String>): String? =
    setCookie.map { it.substringBefore(';').trim() }.firstOrNull { it.startsWith("nouride_session=") && it.length > 16 }
}
