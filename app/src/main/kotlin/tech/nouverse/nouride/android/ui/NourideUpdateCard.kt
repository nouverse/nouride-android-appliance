package tech.nouverse.nouride.android.ui

import android.app.Activity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import tech.nouverse.nouride.android.R

/**
 * A new Nouride, on Home: a small card at the top, as Nouride Home's own update has (`UpdateCard`), with one button that
 * opens Versions and updates and starts the update there (`NourideCard`), where its progress and its restart are
 * followed. Only while this phone's Nouride answers and can put it in (`NourideNotice`).
 */
internal class NourideUpdateCard(private val activity: Activity, ui: Ui, onUpdate: () -> Unit) {
  val view: LinearLayout = ui.card(fill = ui.color(R.color.brand_soft)).apply { visibility = View.GONE }
  private val body: TextView = ui.text(15f, R.color.muted)

  init {
    view.addView(ui.text(17f, R.color.ink, bold = true).apply { setText(R.string.nouride_update_title) })
    view.addView(body, ui.fullWidth(6))
    view.addView(ui.primary { onUpdate() }.apply { setText(R.string.update_button) }, ui.fullWidth(14))
  }

  /** [version]: the one waiting, or null for no card. */
  fun render(version: String?) {
    view.visibility = if (version == null) View.GONE else View.VISIBLE
    if (version != null) body.text = activity.getString(R.string.nouride_update_body, version)
  }
}
