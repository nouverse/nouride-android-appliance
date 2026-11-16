package tech.nouverse.nouride.android.ui

/** The three tabs along the bottom of the app: Nouride itself, opening it elsewhere, and everything else. */
enum class Tab { HOME, CONNECT, MORE }

/**
 * What the Connect tab can offer: nothing to reach yet, Nouride kept to this phone (its switch), no Wi-Fi to reach it
 * over, or the address and its code.
 */
enum class Reach { OFF, THIS_PHONE, NO_WIFI, READY }

/** Which tab shows what, and where Back goes. Pure, so the rules are tested without a screen. */
object Tabs {
  /**
   * The bar is hidden while the first start sets Nouride up: that takes minutes and its progress is the one thing to
   * watch. Every later start is seconds long and keeps the bar.
   */
  fun barShown(look: Look, firstStart: Boolean): Boolean = !(look.busy && firstStart)

  /** Back from Connect or More goes Home, as the bar's own Home would; from Home it leaves the app. */
  fun back(from: Tab): Tab? = if (from == Tab.HOME) null else Tab.HOME

  fun reach(look: Look, lan: String?, openToOthers: Boolean): Reach = when {
    !StatusLook.canShare(look) -> Reach.OFF
    !openToOthers -> Reach.THIS_PHONE
    lan == null -> Reach.NO_WIFI
    else -> Reach.READY
  }
}
