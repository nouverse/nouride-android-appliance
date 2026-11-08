package tech.nouverse.nouride.android.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import tech.nouverse.nouride.android.Nouride
import tech.nouverse.nouride.android.R

/**
 * The Connect tab: Nouride on a tablet, a computer or another phone on the same Wi-Fi. A QR code its camera opens, the
 * address large to type or share, and how to sign in there. Before Nouride is running, or off Wi-Fi, the cat says so
 * instead: an address that leads nowhere is not shown.
 */
internal class ConnectTab(private val activity: Activity, private val ui: Ui, private val onGoHome: () -> Unit) {
  private val column = LinearLayout(activity).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(24))
  }
  private var shown: Pair<Reach, String?>? = null

  val view: ScrollView = ScrollView(activity).apply { addView(column) }

  /** Rebuilt only when what it offers changed: the Wi-Fi's address can change while the tab is open. */
  fun render(look: Look) {
    val lan = SupportText.lanAddress()
    val reach = Tabs.reach(look, lan)
    val address = if (reach == Reach.READY) "http://$lan:${Nouride.PORT}" else null
    if (shown == reach to address) return
    shown = reach to address
    column.removeAllViews()
    column.addView(ui.header(R.string.tab_connect))
    when (reach) {
      Reach.OFF -> column.addView(notYet(R.drawable.mascot_hello, R.string.connect_off_title, R.string.connect_off_body, home = true), ui.fullWidth(16))
      Reach.NO_WIFI -> column.addView(notYet(R.drawable.mascot_lean, R.string.no_wifi_title, R.string.no_wifi, home = false), ui.fullWidth(16))
      Reach.READY -> ready(address!!)
    }
  }

  private fun notYet(image: Int, heading: Int, sentence: Int, home: Boolean): View =
    ui.card(padding = 24).apply {
      gravity = Gravity.CENTER_HORIZONTAL
      addView(ImageView(activity).apply {
        setImageResource(image)
        adjustViewBounds = true
      }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, ui.dp(160)))
      addView(ui.text(20f, R.color.ink, bold = true, center = true).apply { setText(heading) }, ui.fullWidth(16))
      addView(ui.text(15f, R.color.muted, center = true).apply { setText(sentence) }, ui.fullWidth(6))
      if (home) addView(ui.primary { onGoHome() }.apply { setText(R.string.go_home) }, ui.fullWidth(20))
    }

  private fun ready(address: String) {
    val card = ui.card(padding = 24).apply { gravity = Gravity.CENTER_HORIZONTAL }
    card.addView(ui.text(20f, R.color.ink, bold = true, center = true).apply { setText(R.string.connect_ready_title) })
    card.addView(ui.text(15f, R.color.muted, center = true).apply { setText(R.string.connect_ready_body) }, ui.fullWidth(6))
    // Dark on white in the dark theme too: a camera reads a code by its contrast.
    card.addView(ImageView(activity).apply {
      setImageDrawable(BitmapDrawable(activity.resources, qr(address)).apply { isFilterBitmap = false })
      background = ui.rounded(Color.WHITE, 20)
      setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12))
      contentDescription = activity.getString(R.string.connect_qr_description)
    }, LinearLayout.LayoutParams(ui.dp(232), ui.dp(232)).apply { topMargin = ui.dp(18) })
    card.addView(ui.text(22f, R.color.brand, bold = true, center = true).apply {
      text = address
      setTextIsSelectable(true)
      maxLines = 1
      if (Build.VERSION.SDK_INT >= 26) setAutoSizeTextTypeUniformWithConfiguration(14, 22, 1, TypedValue.COMPLEX_UNIT_SP)
      background = ui.rounded(ui.color(R.color.brand_soft), 20)
      setPadding(ui.dp(12), ui.dp(14), ui.dp(12), ui.dp(14))
    }, ui.fullWidth(18))
    card.addView(LinearLayout(activity).apply {
      orientation = LinearLayout.HORIZONTAL
      addView(ui.secondary { copy(address) }.apply { setText(R.string.copy) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
      addView(View(activity), LinearLayout.LayoutParams(ui.dp(12), 1))
      addView(ui.primary { share(address) }.apply { setText(R.string.share_address) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }, ui.fullWidth(16))
    column.addView(card, ui.fullWidth(16))

    column.addView(ui.card().apply {
      addView(ui.text(17f, R.color.ink, bold = true).apply { setText(R.string.connect_sign_in_title) })
      addView(ui.text(15f, R.color.muted).apply { setText(R.string.connect_sign_in_body) }, ui.fullWidth(6))
    }, ui.fullWidth(12))
  }

  /** The code with its four-module quiet zone, a few pixels a module: the view scales it without smoothing. */
  private fun qr(text: String): Bitmap {
    val modules = QrCode.encode(text)
    val quiet = 4
    val scale = 8
    val side = (modules.size + 2 * quiet) * scale
    val pixels = IntArray(side * side) { Color.WHITE }
    for (row in modules.indices) for (column in modules.indices) {
      if (!modules[row][column]) continue
      for (dy in 0 until scale) for (dx in 0 until scale) {
        pixels[((row + quiet) * scale + dy) * side + (column + quiet) * scale + dx] = Color.BLACK
      }
    }
    return Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888)
  }

  private fun copy(address: String) {
    activity.getSystemService(ClipboardManager::class.java)
      .setPrimaryClip(ClipData.newPlainText(activity.getString(R.string.app_name), address))
    Toast.makeText(activity, R.string.copied, Toast.LENGTH_SHORT).show()
  }

  private fun share(address: String) {
    activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, address), null))
  }
}
