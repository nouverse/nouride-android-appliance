package tech.nouverse.nouride.android.ui

import tech.nouverse.nouride.android.R

/**
 * Where a waiting update is said outside its own card: the notification's second line, More's dot and its Versions line.
 * Nouride Home's update (`Updates`) and Nouride's (`NourideNotice`) are said alike, so neither is found only by opening
 * More, then Versions and updates. Pure, so every pair has its words.
 */
object UpdateNotices {
  fun notification(app: Boolean, nouride: Boolean): Int = when {
    app && nouride -> R.string.notification_updates
    app -> R.string.notification_update
    nouride -> R.string.notification_nouride_update
    else -> R.string.notification_tap
  }

  /** More's Versions line when something waits there, or null for the versions themselves. */
  fun versionsRow(app: Boolean, nouride: Boolean): Int? = when {
    app && nouride -> R.string.versions_row_updates
    app -> R.string.versions_row_update
    nouride -> R.string.versions_row_nouride_update
    else -> null
  }

  /** The dot on More: something there needs the person, the battery setting or an update waiting in Versions. */
  fun moreDot(mayStop: Boolean, app: Boolean, nouride: Boolean): Boolean = mayStop || app || nouride
}
