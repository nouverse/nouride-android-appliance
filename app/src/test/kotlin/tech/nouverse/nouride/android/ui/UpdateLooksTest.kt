package tech.nouverse.nouride.android.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.nouverse.nouride.android.R
import tech.nouverse.nouride.android.ui.AppUpdater.State

class UpdateLooksTest {
  private val looks = UpdateStep.values().map(UpdateLooks::of)

  @Test
  fun `a release found is offered on the card with Update`() {
    assertEquals(UpdateStep.READY, UpdateLooks.step(State.IDLE, canInstall = true, installFailed = false))
    val look = UpdateLooks.of(UpdateStep.READY)
    assertEquals(R.string.update_body, look.words)
    assertEquals(UpdateButton.UPDATE, look.button)
    assertEquals(R.string.update_button, look.label)
  }

  @Test
  fun `without Android's permission the card itself asks for it, with Allow`() {
    assertEquals(UpdateStep.ALLOW, UpdateLooks.step(State.IDLE, canInstall = false, installFailed = false))
    val look = UpdateLooks.of(UpdateStep.ALLOW)
    assertEquals(R.string.update_allow_body, look.words)
    assertEquals(UpdateButton.ALLOW, look.button)
    assertEquals(R.string.update_allow, look.label)
  }

  @Test
  fun `the permission comes before a retry, which could not go anywhere without it`() {
    assertEquals(UpdateStep.ALLOW, UpdateLooks.step(State.FAILED, canInstall = false, installFailed = false))
    assertEquals(UpdateStep.ALLOW, UpdateLooks.step(State.IDLE, canInstall = false, installFailed = true))
  }

  @Test
  fun `a download or an install under way is followed, with nothing to press`() {
    for (allowed in listOf(true, false)) {
      assertEquals(UpdateStep.DOWNLOADING, UpdateLooks.step(State.DOWNLOADING, allowed, installFailed = true))
      assertEquals(UpdateStep.INSTALLING, UpdateLooks.step(State.INSTALLING, allowed, installFailed = true))
    }
    assertTrue(UpdateLooks.of(UpdateStep.DOWNLOADING).progress)
    assertEquals(UpdateButton.NONE, UpdateLooks.of(UpdateStep.DOWNLOADING).button)
    assertEquals(UpdateButton.NONE, UpdateLooks.of(UpdateStep.INSTALLING).button)
  }

  @Test
  fun `a failed download and a failed install each say so, and Try again updates`() {
    assertEquals(UpdateStep.DOWNLOAD_FAILED, UpdateLooks.step(State.FAILED, canInstall = true, installFailed = false))
    assertEquals(UpdateStep.INSTALL_FAILED, UpdateLooks.step(State.IDLE, canInstall = true, installFailed = true))
    for (step in listOf(UpdateStep.DOWNLOAD_FAILED, UpdateStep.INSTALL_FAILED)) {
      assertEquals(UpdateButton.UPDATE, UpdateLooks.of(step).button)
      assertEquals(R.string.update_retry, UpdateLooks.of(step).label)
    }
  }

  @Test
  fun `every step has words, and a button has a label`() {
    for (look in looks) {
      assertNotEquals(0, look.words)
      assertEquals(look.button == UpdateButton.NONE, look.label == 0)
    }
  }

  @Test
  fun `the Versions screen asks for the permission on its card too`() {
    val look = VersionLooks.app(AppStep.ALLOW)
    assertEquals(VersionButton.ALLOW, look.button)
    assertEquals(R.string.update_allow_body, look.words)
    assertEquals(R.string.update_allow, look.label)
  }

  /** The card fills every sentence with the version, then the percent (`UpdateCard.render`): none shows a placeholder. */
  @Test
  fun `every sentence formats with the version and a percent`() {
    val file = listOf(File("src/main/res/values/strings.xml"), File("app/src/main/res/values/strings.xml")).first { it.exists() }
    val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
    val strings = (0 until nodes.length).associate { i ->
      nodes.item(i).attributes.getNamedItem("name").nodeValue to nodes.item(i).textContent.replace("\\'", "'")
    }
    val names = R.string::class.java.fields.associate { it.getInt(null) to it.name }
    for (id in looks.map { it.words }.toSet()) {
      val name = names.getValue(id)
      val text = String.format(strings.getValue(name), "0.2.1", 42)
      assertFalse("$name: $text", Regex("%(\\d+\\$)?[sd]").containsMatchIn(text))
    }
    assertTrue(String.format(strings.getValue("update_downloading"), "0.2.1", 42).contains("42"))
    assertTrue(String.format(strings.getValue("update_body"), "0.2.1", 42).contains("0.2.1"))
  }
}
