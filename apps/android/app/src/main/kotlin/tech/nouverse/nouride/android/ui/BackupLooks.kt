package tech.nouverse.nouride.android.ui

import tech.nouverse.nouride.android.R
import tech.nouverse.nouride.android.backup.BackupStatus
import tech.nouverse.nouride.android.backup.BackupStatus.Step
import tech.nouverse.nouride.android.server.Where

/** The words of More's "Nouride's files" card for each step (`backup/BackupStatus`). Pure, so every step has its words. */
object BackupLooks {
  /** Only for this phone's own Nouride, once installed: on another computer its files are there, not here. */
  fun shown(where: Where?, installed: Boolean): Boolean = where != Where.REMOTE && installed

  /** What the zip holds of the secrets: sealed to the backup password, or nothing of them. */
  fun secrets(status: BackupStatus): Int = if (status.sealed) R.string.backup_sealed else R.string.backup_unsealed

  /** Its size once counted, with the size as its argument; until then, that it is being counted. */
  fun size(status: BackupStatus): Int = if (status.size == null) R.string.backup_counting else R.string.backup_size

  /** The line under the button: where it is, or what happened last; 0 for none. */
  fun result(step: Step): Int = when (step) {
    Step.IDLE -> 0
    Step.SNAPSHOT -> R.string.backup_snapshot
    Step.SAVING -> R.string.backup_saving
    Step.SAVED -> R.string.backup_saved
    Step.NO_ROOM -> R.string.backup_no_room
    Step.NO_SNAPSHOT -> R.string.backup_no_snapshot
    Step.FAILED -> R.string.backup_failed
  }

  /** Pressed only once its size is known, and never again while a zip is made. */
  fun canStart(status: BackupStatus): Boolean = status.size != null && !status.busy

  /** A bar while it moves: no number while Nouride takes its snapshot. */
  fun bar(step: Step): Boolean = step == Step.SNAPSHOT || step == Step.SAVING

  /** The bar's number, or null while it cannot be counted. */
  fun percent(status: BackupStatus): Int? = if (status.step == Step.SAVING) status.percent else null
}
