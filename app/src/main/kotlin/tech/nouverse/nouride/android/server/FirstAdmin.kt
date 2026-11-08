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
 * should not keep a password it no longer needs. With `INITIAL_ADMIN_MUST_CHANGE=1` beside them: the dashboard opens
 * signed in on its first screen, "Choose the username and password you will sign in with from now on" (`/welcome`),
 * before the setup wizard. The owner, 2026-11-07: a generated account the person never saw left them no way to sign in
 * from another device, and the password could not be changed without the old one. The app signs in with its password
 * each time it opens the dashboard; once the person has chosen their own, that fails once, the app forgets its password
 * ([forgets]), and the session they hold, or the dashboard's own login page, takes over.
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
      "${PREFIX}USERNAME=$USERNAME\n${PREFIX}PASSWORD=$password\n${PREFIX}MUST_CHANGE=1\n"

  /** The `.env` without any `INITIAL_ADMIN_*` line; every other line exactly as it was. */
  fun withoutAccount(env: String): String =
    env.split("\n").filterNot { it.trimStart().startsWith(PREFIX) }.joinToString("\n")

  /**
   * Whether the app forgets its password after a sign-in that answered [code]. A 401 means the person chose their own
   * username and password, so this one will never work again: kept, every opening of the dashboard was another failed
   * sign-in in the dashboard's history, and spent the allowance the person's own sign-ins share (Nouride takes ten
   * failures an account and thirty an address in fifteen minutes, then refuses even the right password for a while).
   * Anything else may pass: too many attempts, the daemon still starting, no answer at all.
   */
  fun forgets(code: Int): Boolean = code == 401

  /** The session cookie, `nouride_session=<token>`, from a sign-in's `Set-Cookie` headers; null when there is none. */
  fun sessionCookie(setCookie: List<String>): String? =
    setCookie.map { it.substringBefore(';').trim() }.firstOrNull { it.startsWith("nouride_session=") && it.length > 16 }
}
