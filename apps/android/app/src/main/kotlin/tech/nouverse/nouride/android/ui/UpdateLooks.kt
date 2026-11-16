package tech.nouverse.nouride.android.ui

import tech.nouverse.nouride.android.R

/** Where Home's card for a new Nouride Home is. */
enum class UpdateStep { READY, ALLOW, DOWNLOADING, INSTALLING, DOWNLOAD_FAILED, INSTALL_FAILED }

/** What the card's one button does: start the update, or open the setting that lets this app install it. */
enum class UpdateButton { UPDATE, ALLOW, NONE }

/** The card's sentence and button. [words] may take the version, then a percent; [label] is 0 with no button. */
data class UpdateLook(val words: Int, val button: UpdateButton, val label: Int, val progress: Boolean)

/**
 * Home's card for a new Nouride Home (`UpdateCard`). Pure, as `VersionLooks` is, so a step cannot ship without words.
 * Everything the update needs from the person is said on the card itself, Android's permission to install included:
 * nothing comes over the screen for it.
 */
internal object UpdateLooks {
  /**
   * [canInstall]: Android lets this app install its update (`Updates.canInstall`). [installFailed]: the last install
   * did not go in (`Updates.failed`). A download or an install under way comes first; then the permission, without
   * which neither Update nor Try again could go anywhere.
   */
  fun step(state: AppUpdater.State, canInstall: Boolean, installFailed: Boolean): UpdateStep = when {
    state == AppUpdater.State.DOWNLOADING -> UpdateStep.DOWNLOADING
    state == AppUpdater.State.INSTALLING -> UpdateStep.INSTALLING
    !canInstall -> UpdateStep.ALLOW
    state == AppUpdater.State.FAILED -> UpdateStep.DOWNLOAD_FAILED
    installFailed -> UpdateStep.INSTALL_FAILED
    else -> UpdateStep.READY
  }

  fun of(step: UpdateStep): UpdateLook = when (step) {
    UpdateStep.READY -> UpdateLook(R.string.update_body, UpdateButton.UPDATE, R.string.update_button, progress = false)
    UpdateStep.ALLOW -> UpdateLook(R.string.update_allow_body, UpdateButton.ALLOW, R.string.update_allow, progress = false)
    UpdateStep.DOWNLOADING -> UpdateLook(R.string.update_downloading, UpdateButton.NONE, 0, progress = true)
    UpdateStep.INSTALLING -> UpdateLook(R.string.update_installing, UpdateButton.NONE, 0, progress = false)
    UpdateStep.DOWNLOAD_FAILED -> UpdateLook(R.string.update_failed, UpdateButton.UPDATE, R.string.update_retry, progress = false)
    UpdateStep.INSTALL_FAILED -> UpdateLook(R.string.update_install_failed, UpdateButton.UPDATE, R.string.update_retry, progress = false)
  }
}
