package tech.nouverse.nouride.android.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import tech.nouverse.nouride.android.R

/**
 * The bar along the bottom: Home, Connect and More, each an icon over its name, the open one in a soft pill as Android's
 * own navigation bars draw it. Drawn here, as the app takes no third-party library. A dot on a tab says something there
 * needs the person: on More, that the phone may stop Nouride with the screen off.
 */
internal class BottomBar(context: Context, private val ui: Ui, onSelect: (Tab) -> Unit) {
  private class Item(val pill: FrameLayout, val icon: ImageView, val label: TextView, val dot: View, val root: View)

  private val items = LinkedHashMap<Tab, Item>()

  val view: LinearLayout = LinearLayout(context).apply {
    orientation = LinearLayout.HORIZONTAL
    // It floats above the page as a card does, as the dashboard's tab bar on a phone.
    background = ui.rounded(ui.color(R.color.surface), 28, ui.color(R.color.line))
    ui.float(this, 6)
    setPadding(ui.dp(6), ui.dp(8), ui.dp(6), ui.dp(8))
  }

  init {
    for ((tab, icon, label) in listOf(
      Triple(Tab.HOME, R.drawable.ic_tab_home, R.string.tab_home),
      Triple(Tab.CONNECT, R.drawable.ic_tab_connect, R.string.tab_connect),
      Triple(Tab.MORE, R.drawable.ic_tab_more, R.string.tab_more),
    )) {
      val iconView = ImageView(context).apply { setImageResource(icon) }
      val dot = View(context).apply {
        background = GradientDrawable().apply {
          shape = GradientDrawable.OVAL
          setColor(ui.color(R.color.warn))
        }
        visibility = View.GONE
      }
      val pill = FrameLayout(context).apply {
        addView(iconView, FrameLayout.LayoutParams(ui.dp(24), ui.dp(24), Gravity.CENTER))
        addView(dot, FrameLayout.LayoutParams(ui.dp(9), ui.dp(9), Gravity.TOP or Gravity.END).apply {
          setMargins(0, ui.dp(3), ui.dp(16), 0)
        })
      }
      val text = ui.text(12f, R.color.muted, center = true).apply { setText(label) }
      val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        isClickable = true
        contentDescription = context.getString(label)
        background = RippleDrawable(ColorStateList.valueOf(ui.color(R.color.place_soft)), null, null)
        setOnClickListener { onSelect(tab) }
        addView(pill, LinearLayout.LayoutParams(ui.dp(64), ui.dp(32)))
        addView(text, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
          topMargin = ui.dp(4)
        })
      }
      view.addView(root, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
      items[tab] = Item(pill, iconView, text, dot, root)
    }
  }

  fun select(tab: Tab) {
    for ((each, item) in items) {
      val open = each == tab
      item.pill.background = if (open) ui.rounded(ui.color(R.color.place_soft), 16) else null
      item.icon.imageTintList = ColorStateList.valueOf(ui.color(if (open) R.color.place else R.color.muted))
      item.label.setTextColor(ui.color(if (open) R.color.ink else R.color.muted))
      item.label.typeface = Fonts.typeface(item.label.context, if (open) Type.STRONG else Type.BODY)
      item.root.isSelected = open
    }
  }

  /** The dot on [tab]: something there needs the person. */
  fun attention(tab: Tab, on: Boolean) {
    items[tab]?.dot?.visibility = if (on) View.VISIBLE else View.GONE
  }
}
