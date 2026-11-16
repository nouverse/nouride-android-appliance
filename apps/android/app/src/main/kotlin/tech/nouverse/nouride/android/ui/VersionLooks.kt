package tech.nouverse.nouride.android.ui

import tech.nouverse.nouride.android.R
import tech.nouverse.nouride.android.update.NourideDownload
import tech.nouverse.nouride.android.update.NourideStatus
import tech.nouverse.nouride.android.update.Restart

/** Where the Versions screen's Nouride Home card is. */
enum class AppStep { CHECKING, UP_TO_DATE, READY, CHECK_FAILED, DOWNLOADING, INSTALLING, DOWNLOAD_FAILED, INSTALL_FAILED }

/** Where its Nouride card is. */
enum class NourideStep {
  OFF, CHECKING, UP_TO_DATE, READY, CANNOT, CHECK_FAILED, DOWNLOADING, DOWNLOADED, INSTALLING,
  RESTARTING_NOW, RESTARTING_IDLE, RESTARTING_JOBS, DOWNLOAD_FAILED, INSTALL_FAILED,
}

/** What a card's one button does: ask again, fetch and put in the new version, put in the one already fetched. */
enum class VersionButton { CHECK, UPDATE, INSTALL, NONE }

/** A card's sentence and button. [words] and [label] may take the version, then a percent; [label] is 0 with no button. */
data class VersionLook(val words: Int, val button: VersionButton, val label: Int, val progress: Boolean)

/**
 * The Versions screen (`VersionsActivity`), opened from More: this app's version and Nouride's, each with a check and its
 * update. Pure, as `StatusLook` is, so neither card has a step without words: the `when`s do not compile without them.
 */
object VersionLooks {
  fun app(step: AppStep): VersionLook = when (step) {
    AppStep.CHECKING -> VersionLook(R.string.checking, VersionButton.NONE, 0, progress = false)
    AppStep.UP_TO_DATE -> VersionLook(R.string.up_to_date, VersionButton.CHECK, R.string.check_updates, progress = false)
    AppStep.READY -> VersionLook(R.string.versions_app_ready, VersionButton.UPDATE, R.string.versions_update_to, progress = false)
    AppStep.CHECK_FAILED -> VersionLook(R.string.check_failed, VersionButton.CHECK, R.string.update_retry, progress = false)
    AppStep.DOWNLOADING -> VersionLook(R.string.versions_downloading, VersionButton.NONE, 0, progress = true)
    AppStep.INSTALLING -> VersionLook(R.string.update_installing, VersionButton.NONE, 0, progress = false)
    AppStep.DOWNLOAD_FAILED -> VersionLook(R.string.update_failed, VersionButton.UPDATE, R.string.update_retry, progress = false)
    AppStep.INSTALL_FAILED -> VersionLook(R.string.update_install_failed, VersionButton.UPDATE, R.string.update_retry, progress = false)
  }

  fun nouride(step: NourideStep): VersionLook = when (step) {
    NourideStep.OFF -> VersionLook(R.string.versions_nouride_off, VersionButton.NONE, 0, progress = false)
    NourideStep.CHECKING -> VersionLook(R.string.checking, VersionButton.NONE, 0, progress = false)
    NourideStep.UP_TO_DATE -> VersionLook(R.string.up_to_date, VersionButton.CHECK, R.string.check_updates, progress = false)
    NourideStep.READY -> VersionLook(R.string.versions_nouride_ready, VersionButton.UPDATE, R.string.versions_update_to, progress = false)
    NourideStep.CANNOT -> VersionLook(R.string.versions_nouride_cannot, VersionButton.CHECK, R.string.check_updates, progress = false)
    NourideStep.CHECK_FAILED -> VersionLook(R.string.check_failed, VersionButton.CHECK, R.string.update_retry, progress = false)
    NourideStep.DOWNLOADING -> VersionLook(R.string.versions_downloading, VersionButton.NONE, 0, progress = true)
    NourideStep.DOWNLOADED -> VersionLook(R.string.versions_nouride_downloaded, VersionButton.INSTALL, R.string.versions_install, progress = false)
    NourideStep.INSTALLING -> VersionLook(R.string.versions_nouride_installing, VersionButton.NONE, 0, progress = true)
    NourideStep.RESTARTING_NOW -> VersionLook(R.string.versions_nouride_restarting, VersionButton.NONE, 0, progress = true)
    NourideStep.RESTARTING_IDLE -> VersionLook(R.string.versions_nouride_restarts_idle, VersionButton.NONE, 0, progress = false)
    NourideStep.RESTARTING_JOBS -> VersionLook(R.string.versions_nouride_restarts_jobs, VersionButton.NONE, 0, progress = false)
    NourideStep.DOWNLOAD_FAILED -> VersionLook(R.string.update_failed, VersionButton.UPDATE, R.string.update_retry, progress = false)
    NourideStep.INSTALL_FAILED -> VersionLook(R.string.update_install_failed, VersionButton.INSTALL, R.string.update_retry, progress = false)
  }

  /**
   * The step Nouride's answer puts its card in. A download already under way or done comes first, whoever started it
   * (this screen, or the dashboard's Update page): the card follows it rather than offering a second one.
   */
  fun nourideStep(status: NourideStatus): NourideStep = when {
    status.download.status == NourideDownload.Status.DOWNLOADING -> NourideStep.DOWNLOADING
    status.download.status == NourideDownload.Status.READY && status.ready != null -> NourideStep.DOWNLOADED
    status.checkFailed -> NourideStep.CHECK_FAILED
    status.ready == null -> NourideStep.UP_TO_DATE
    !status.canUpdate -> NourideStep.CANNOT
    status.download.status == NourideDownload.Status.FAILED -> NourideStep.DOWNLOAD_FAILED
    else -> NourideStep.READY
  }

  /** What the card says once the new version is in, from when Nouride said it would restart. */
  fun restarting(restart: Restart): NourideStep = when (restart) {
    Restart.NOW -> NourideStep.RESTARTING_NOW
    Restart.IDLE -> NourideStep.RESTARTING_IDLE
    Restart.JOBS -> NourideStep.RESTARTING_JOBS
  }
}
