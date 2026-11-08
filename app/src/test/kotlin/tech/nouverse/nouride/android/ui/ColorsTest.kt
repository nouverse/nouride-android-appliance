package tech.nouverse.nouride.android.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every pair of colours that carries words reads at WCAG AA, 4.5:1, by day and at night, as the dashboard's
 * `nouverse-look.test.ts` holds for its own. The palette is Nouride's soft look, where by day a pastel cannot carry words
 * at AA: a new colour or a new pair goes in this list before it ships.
 */
class ColorsTest {
  private fun palette(folder: String): Map<String, String> {
    // Gradle runs unit tests from the module's folder; an IDE may run them from the project's.
    val file = listOf(File("src/main/res/$folder/colors.xml"), File("app/src/main/res/$folder/colors.xml")).first { it.exists() }
    val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("color")
    return (0 until nodes.length).associate { i ->
      val node = nodes.item(i)
      node.attributes.getNamedItem("name").nodeValue to node.textContent.trim()
    }
  }

  private fun luminance(hex: String): Double {
    val rgb = hex.removePrefix("#").chunked(2).map { it.toInt(16) / 255.0 }
    val (r, g, b) = rgb.map { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
  }

  private fun contrast(a: String, b: String): Double {
    val (light, dark) = listOf(luminance(a), luminance(b)).sortedDescending()
    return (light + 0.05) / (dark + 0.05)
  }

  /** Words on a ground, as the screens put them. */
  private val pairs = listOf(
    "ink" to "background", "ink" to "surface", "muted" to "background", "muted" to "surface",
    "on_brand" to "brand",
    "brand_text" to "surface", "brand_text" to "brand_soft", "ink" to "brand_soft", "muted" to "brand_soft",
    "place" to "surface", "ink" to "place_soft",
    "good" to "surface", "ink" to "good_soft", "muted" to "good_soft",
    "warn" to "surface", "ink" to "warn_soft", "muted" to "warn_soft",
    "ink" to "hero_light", "muted" to "hero_light",
  )

  @Test
  fun `every pair of words and ground reads at AA in both themes`() {
    val day = palette("values")
    val night = palette("values-night")
    assertEquals("the night palette names the same colours", day.keys, night.keys)
    for ((name, colors) in listOf("day" to day, "night" to night)) for ((words, ground) in pairs) {
      val ratio = contrast(colors.getValue(words), colors.getValue(ground))
      assertTrue("$name: $words on $ground is ${"%.2f".format(ratio)}:1", ratio >= 4.5)
    }
  }

  @Test
  fun `the main button's words are dark ink on the peach, never white`() {
    for (folder in listOf("values", "values-night")) {
      val colors = palette(folder)
      assertNotEquals(folder, "#FFFFFF", colors.getValue("on_brand").uppercase())
      assertTrue(folder, luminance(colors.getValue("on_brand")) < luminance(colors.getValue("brand")))
    }
  }
}
