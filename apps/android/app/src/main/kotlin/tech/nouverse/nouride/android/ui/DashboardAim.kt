package tech.nouverse.nouride.android.ui

import tech.nouverse.nouride.android.Nouride
import tech.nouverse.nouride.android.server.RemoteHost

/**
 * Which Nouride the dashboard's screen shows, from where Nouride lives (`server/Place`): this phone's own, which the
 * app signs in to, or the machine at another address, whose own login page is the way in.
 */
data class DashboardAim(val start: String, val origin: String, val local: Boolean) {
  companion object {
    fun of(remote: String?): DashboardAim = DashboardAim(
      start = remote?.let(RemoteHost::dashboard) ?: Nouride.DASHBOARD_URL,
      origin = remote ?: Nouride.ORIGIN,
      local = remote == null,
    )

    /**
     * The screen opened again while it is still alive (it is `singleTask`, and the notification or the launcher can put
     * the status screen above it): the Nouride to show now when it moved since the screen was made, or null when it is
     * the one on screen, which stays as it is. Without it, More's choice of another machine (or of this phone) followed
     * by Open Nouride brought back the dashboard of the Nouride just left.
     */
    fun onOpen(shown: DashboardAim, remote: String?): DashboardAim? = of(remote).takeIf { it.origin != shown.origin }
  }
}
