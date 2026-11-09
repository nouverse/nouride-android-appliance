package tech.nouverse.nouride.android.ui

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import tech.nouverse.nouride.android.R

/**
 * One card of the Versions screen: what it is, its version, one sentence of where its update is, a bar while something
 * moves, and at most one button. A new version to take is the main button; Check for updates a quieter one.
 */
internal class VersionCard(private val context: Context, ui: Ui, titleId: Int, private val onButton: (VersionButton) -> Unit) {
  val view: LinearLayout = ui.card()
  private val version: TextView = ui.text(15f, R.color.muted)
  private val words: TextView = ui.text(15f, R.color.ink)
  private val bar: ProgressBar = ui.progressBar()
  private var button = VersionButton.NONE
  private val main: Button = ui.primary { onButton(button) }
  private val quiet: Button = ui.secondary { onButton(button) }

  init {
    view.addView(ui.text(17f, R.color.ink, bold = true).apply { setText(titleId) })
    view.addView(version, ui.fullWidth(2))
    view.addView(words, ui.fullWidth(10))
    view.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(12)).apply { topMargin = ui.dp(14) })
    view.addView(main, ui.fullWidth(14))
    view.addView(quiet, ui.fullWidth(12))
  }

  /** [installed]: the version running, or null when it is not known. [next]: the version on offer, for the words. */
  fun render(look: VersionLook, installed: String?, next: String?, percent: Int) {
    version.visibility = if (installed == null) View.GONE else View.VISIBLE
    if (installed != null) version.text = context.getString(R.string.app_version, installed)
    words.text = context.getString(look.words, next ?: "", percent)
    bar.visibility = if (look.progress) View.VISIBLE else View.GONE
    bar.isIndeterminate = percent == 0
    bar.progress = percent
    button = look.button
    val label = if (look.label == 0) "" else context.getString(look.label, next ?: "")
    main.visibility = if (look.button == VersionButton.UPDATE || look.button == VersionButton.INSTALL) View.VISIBLE else View.GONE
    quiet.visibility = if (look.button == VersionButton.CHECK) View.VISIBLE else View.GONE
    main.text = label
    quiet.text = label
  }
}
