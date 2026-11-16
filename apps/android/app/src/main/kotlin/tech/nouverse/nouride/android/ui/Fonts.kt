package tech.nouverse.nouride.android.ui

import android.content.Context
import android.graphics.Typeface

/** How a line of words is set. */
enum class Type { HEADING, STRONG, BODY, MONO }

/**
 * Nouride's type, as the dashboard serves it (the design rules): Fredoka 500 for headings, Manrope for everything else,
 * DM Mono for an address or a log. The files in `assets/fonts` are the dashboard's own Latin subsets, made static (their
 * OFL licences beside them); a letter outside them is drawn in the phone's own font. From assets, not `res/font`, which
 * needs Android 8 and the app runs on 7.
 */
internal object Fonts {
  private val loaded = HashMap<Type, Typeface>()

  /** Big bold words are a heading, small bold ones a label: the screens say only a size and whether it is bold. */
  fun of(sizeSp: Float, bold: Boolean): Type = when {
    bold && sizeSp >= HEADING_FROM_SP -> Type.HEADING
    bold -> Type.STRONG
    else -> Type.BODY
  }

  fun file(type: Type): String = when (type) {
    Type.HEADING -> "fredoka_medium.ttf"
    Type.STRONG -> "manrope_semibold.ttf"
    Type.BODY -> "manrope_regular.ttf"
    Type.MONO -> "dm_mono.ttf"
  }

  fun typeface(context: Context, type: Type): Typeface =
    loaded.getOrPut(type) { Typeface.createFromAsset(context.applicationContext.assets, "fonts/${file(type)}") }

  /** The smallest heading: a card's title. Smaller bold words are labels (a step, a tab, a percent). */
  const val HEADING_FROM_SP = 17f
}
