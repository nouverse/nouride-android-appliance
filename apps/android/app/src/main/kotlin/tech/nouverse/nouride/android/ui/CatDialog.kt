package tech.nouverse.nouride.android.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import tech.nouverse.nouride.android.R

/** A round card over the screen, as "Use from another device" is: the cat, a title, a sentence, one button and Later. */
internal fun catDialog(activity: Activity, ui: Ui, image: Int, heading: String, sentence: String, action: Int, onAction: () -> Unit): Dialog {
  val dialog = Dialog(activity)
  val content = ui.card(padding = 24, floating = false).apply { gravity = Gravity.CENTER_HORIZONTAL }
  content.addView(ImageView(activity).apply {
    setImageResource(image)
    adjustViewBounds = true
  }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, ui.dp(120)))
  content.addView(ui.text(20f, R.color.ink, bold = true, center = true).apply { text = heading }, ui.fullWidth(12))
  content.addView(ui.text(15f, R.color.muted, center = true).apply { text = sentence }, ui.fullWidth(6))
  content.addView(ui.primary {
    dialog.dismiss()
    onAction()
  }.apply { setText(action) }, ui.fullWidth(20))
  content.addView(ui.quiet { dialog.dismiss() }.apply { setText(R.string.later) }, ui.fullWidth(4))
  dialog.setContentView(content)
  dialog.window?.apply {
    setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    setLayout(minOf(activity.resources.displayMetrics.widthPixels - ui.dp(32), ui.dp(420)), ViewGroup.LayoutParams.WRAP_CONTENT)
  }
  dialog.show()
  return dialog
}

/** Android's question, asked once: let this app install its own updates. */
internal fun askToAllowInstalls(activity: Activity, ui: Ui, onAllow: () -> Unit): Dialog =
  catDialog(activity, ui, R.drawable.mascot_pointing, activity.getString(R.string.update_allow_title),
    activity.getString(R.string.update_allow_body), R.string.update_allow, onAllow)
