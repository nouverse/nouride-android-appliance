package tech.nouverse.nouride.android.backup

/** Where "Download Nouride's files" is: what More's card shows (`ui/BackupLooks`). */
data class BackupStatus(
  /** What the zip may weigh, once counted: the files that go in and the database as it is now. */
  val size: Long? = null,
  /** Whether Nouride keeps its secrets sealed to a backup password (`data/secrets.sealed`), which goes in. */
  val sealed: Boolean = false,
  val step: Step = Step.IDLE,
  val done: Long = 0,
  val total: Long = 0,
  /** The name the last zip got in Downloads. */
  val saved: String? = null,
) {
  enum class Step {
    IDLE,

    /** Nouride takes its database's snapshot. */
    SNAPSHOT,

    /** The zip is written: [done] of [total] bytes read. */
    SAVING,
    SAVED,

    /** Not enough room in Downloads for [size]. */
    NO_ROOM,

    /** Nouride took no snapshot of its database, so there is no zip. */
    NO_SNAPSHOT,
    FAILED,
  }

  val busy: Boolean get() = step == Step.SNAPSHOT || step == Step.SAVING

  /** How far the zip is, 0 to 100, while it is written. */
  val percent: Int get() = if (total <= 0) 0 else (done * 100 / total).toInt().coerceIn(0, 100)
}
