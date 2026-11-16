package tech.nouverse.nouride.android.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A window over the screen is only for a confirmation (the android-appliance-ui skill): everything else is said on the
 * page, as a card or a screen of its own. Every dialog the app builds is found in its sources, by the function that
 * builds it, so a new one is a decision this list has to be changed for.
 */
class ModalsTest {
  private val confirmations = setOf(
    "StatusActivity.confirmTurnOff",
    "DashboardActivity.askToLeave",
    // The address of another computer, until it is a screen of its own.
    "AddressSheet.addressSheet",
  )

  @Test
  fun `a dialog is built only to confirm something`() {
    val root = listOf(File("src/main/kotlin"), File("app/src/main/kotlin")).first { it.isDirectory }
    val built = root.walk().filter { it.extension == "kt" }.flatMap { file ->
      val text = file.readText()
      // `Dialog(` is one drawn in the app's own look; `AlertDialog.Builder(` is Android's own.
      Regex("""\b(?:AlertDialog\.Builder|Dialog)\(""").findAll(text).map { found ->
        val function = Regex("""\bfun\s+(?:[\w.]+\.)?(\w+)\s*\(""").findAll(text.substring(0, found.range.first)).lastOrNull()
        "${file.nameWithoutExtension}.${function?.groupValues?.get(1) ?: "?"}"
      }
    }.toSet()
    assertEquals(confirmations, built)
  }
}
