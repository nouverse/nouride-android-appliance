package tech.nouverse.nouride.android.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.nouverse.nouride.android.R
import tech.nouverse.nouride.android.update.NourideDownload
import tech.nouverse.nouride.android.update.NourideStatus
import tech.nouverse.nouride.android.update.Restart

class VersionLooksTest {
  private val looks = AppStep.values().map(VersionLooks::app) + NourideStep.values().map(VersionLooks::nouride)

  private fun status(ready: String? = "0.8.25", can: Boolean = true, failed: Boolean = false, download: NourideDownload.Status = NourideDownload.Status.IDLE) =
    NourideStatus("0.8.24", ready, can, failed, NourideDownload(download, 0, null))

  @Test
  fun `every step has words, and a button has a label`() {
    for (look in looks) {
      assertNotEquals(0, look.words)
      assertEquals(look.button == VersionButton.NONE, look.label == 0)
    }
  }

  @Test
  fun `nothing to press while something moves`() {
    for (look in looks.filter { it.progress }) assertEquals(VersionButton.NONE, look.button)
  }

  @Test
  fun `a newer Nouride is offered as Update to it, and a fetched one as Install`() {
    assertEquals(NourideStep.READY, VersionLooks.nourideStep(status()))
    assertEquals(VersionButton.UPDATE, VersionLooks.nouride(NourideStep.READY).button)
    assertEquals(NourideStep.DOWNLOADED, VersionLooks.nourideStep(status(download = NourideDownload.Status.READY)))
    assertEquals(VersionButton.INSTALL, VersionLooks.nouride(NourideStep.DOWNLOADED).button)
  }

  @Test
  fun `a download under way is followed, whoever started it`() {
    assertEquals(NourideStep.DOWNLOADING, VersionLooks.nourideStep(status(download = NourideDownload.Status.DOWNLOADING)))
  }

  @Test
  fun `a check that failed, the newest version, and an update it cannot take each say so`() {
    assertEquals(NourideStep.CHECK_FAILED, VersionLooks.nourideStep(status(ready = null, failed = true)))
    assertEquals(NourideStep.UP_TO_DATE, VersionLooks.nourideStep(status(ready = null)))
    assertEquals(NourideStep.CANNOT, VersionLooks.nourideStep(status(can = false)))
    assertEquals(NourideStep.DOWNLOAD_FAILED, VersionLooks.nourideStep(status(download = NourideDownload.Status.FAILED)))
    // A paused download is picked up again by Update.
    assertEquals(NourideStep.READY, VersionLooks.nourideStep(status(download = NourideDownload.Status.PAUSED)))
  }

  @Test
  fun `a failed download tries the download again, a failed install the install`() {
    assertEquals(VersionButton.UPDATE, VersionLooks.nouride(NourideStep.DOWNLOAD_FAILED).button)
    assertEquals(VersionButton.INSTALL, VersionLooks.nouride(NourideStep.INSTALL_FAILED).button)
  }

  @Test
  fun `Nouride off offers nothing to press`() {
    assertEquals(VersionButton.NONE, VersionLooks.nouride(NourideStep.OFF).button)
  }

  @Test
  fun `the words after the install follow when Nouride said it restarts`() {
    assertEquals(NourideStep.RESTARTING_NOW, VersionLooks.restarting(Restart.NOW))
    assertEquals(NourideStep.RESTARTING_IDLE, VersionLooks.restarting(Restart.IDLE))
    assertEquals(NourideStep.RESTARTING_JOBS, VersionLooks.restarting(Restart.JOBS))
  }

  /**
   * The card fills every sentence and label with the same two values, the version then a percent (`VersionCard`): each
   * one formats with them, and none shows a placeholder.
   */
  @Test
  fun `every sentence and label formats with the version and a percent`() {
    val file = listOf(File("src/main/res/values/strings.xml"), File("app/src/main/res/values/strings.xml")).first { it.exists() }
    val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
    val strings = (0 until nodes.length).associate { i ->
      nodes.item(i).attributes.getNamedItem("name").nodeValue to nodes.item(i).textContent.replace("\\'", "'")
    }
    val names = R.string::class.java.fields.associate { it.getInt(null) to it.name }
    val used = looks.flatMap { listOf(it.words, it.label) }.filter { it != 0 }.toSet()
    for (id in used) {
      val name = names.getValue(id)
      val text = String.format(strings.getValue(name), "0.8.25", 42)
      assertFalse("$name: $text", Regex("%(\\d+\\$)?[sd]").containsMatchIn(text))
    }
    assertTrue(String.format(strings.getValue("versions_downloading"), "0.8.25", 42).contains("42"))
  }
}
