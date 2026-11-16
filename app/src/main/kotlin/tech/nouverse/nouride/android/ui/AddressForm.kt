package tech.nouverse.nouride.android.ui

import tech.nouverse.nouride.android.R
import tech.nouverse.nouride.android.server.Health
import tech.nouverse.nouride.android.server.RemoteHost
import tech.nouverse.nouride.android.server.ServerState
import tech.nouverse.nouride.android.server.Where

/** What the address screen (`AddressActivity`) does with what was typed and with the one check after it. Pure. */
internal object AddressForm {
  /** The sentence under the field for what cannot be an address, each with what to do; null when it can be one. */
  fun refusal(read: RemoteHost.Read): Int? = when (read) {
    is RemoteHost.Read.Address -> null
    RemoteHost.Read.Empty -> R.string.where_empty
    RemoteHost.Read.PlainHttp -> R.string.where_plain_http
    RemoteHost.Read.NotAnAddress -> R.string.where_not_an_address
  }

  /**
   * Whether the address answered the one check before it is taken. Any HTTP answer is, even a page in front of Nouride
   * or a tunnel whose computer is off: Home says how that computer is. No answer at all (a name that does not exist, a
   * computer that cannot be reached) is not, as a typo is the likeliest cause.
   */
  fun answered(health: Health): Boolean = health.httpCode != 0

  /** Nouride runs on this phone now, so connecting turns it off, which the screen says before it happens. */
  fun stopsHere(where: Where?, phase: ServerState.Phase): Boolean = where != Where.REMOTE && StatusLook.canTurnOff(phase)
}
