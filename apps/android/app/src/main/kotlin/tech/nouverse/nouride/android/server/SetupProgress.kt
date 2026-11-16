package tech.nouverse.nouride.android.server

/** Which part of the first start is running: each has its own sentence under the progress bar. */
enum class Step { UNPACK, PACKAGES, TOOLS, NOURIDE, START }

/** What the screen shows of the first start: a step and a percent, 0 to 99 (100 is the ready screen itself). */
data class Progress(val step: Step, val percent: Int)

/**
 * How far the first start is, as a bar a person can watch move. Each step owns a slice of the bar, sized from the first
 * start measured on a Redmi 12 (`experiments/2026-11-06-apk-on-redmi-12`: unpacking 6 s, apt 1 to 4.5 min, the
 * installer 44 s, the first boot under 10 s), so the slices are an estimate. Unpacking moves by the bytes read. A step
 * whose length is unknown moves a little closer to the end of its slice with every line it prints and never reaches
 * it, so the bar never stands still for long and never goes back; only the next step's start fills a slice.
 */
class SetupProgress {
  private var step = Step.UNPACK
  private var exact = 0.0

  fun snapshot() = Progress(step, exact.toInt().coerceIn(0, 99))

  fun unpacked(read: Long, total: Long) {
    enter(Step.UNPACK)
    if (total <= 0) return
    val (from, to) = SLICES.getValue(Step.UNPACK)
    move(from + (to - from) * (read.toDouble() / total).coerceIn(0.0, 1.0))
  }

  /** A line the setup script printed: its marker starts the next step, any other line nudges the bar. */
  fun line(text: String) {
    val marked = Step.values().firstOrNull { marker(it) == text.trim() }
    if (marked != null) return enter(marked)
    if (text.isBlank()) return
    move(exact + (SLICES.getValue(step).second - exact) * NUDGE)
  }

  fun starting() = enter(Step.START)

  private fun enter(next: Step) {
    if (next.ordinal < step.ordinal) return
    step = next
    move(SLICES.getValue(next).first)
  }

  private fun move(to: Double) {
    if (to > exact) exact = to
  }

  companion object {
    /** What the setup script echoes as a step begins (`Nouride.INSTALL`); a person never sees it. */
    fun marker(step: Step) = "@nouride-step ${step.name.lowercase()}"

    private const val NUDGE = 0.04

    private val SLICES = mapOf(
      Step.UNPACK to (0.0 to 10.0),
      Step.PACKAGES to (10.0 to 40.0),
      Step.TOOLS to (40.0 to 75.0),
      Step.NOURIDE to (75.0 to 95.0),
      Step.START to (95.0 to 99.0),
    )
  }
}
