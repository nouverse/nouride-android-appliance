package tech.nouverse.nouride.android.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceTest {
  @Test
  fun `a phone with Nouride on it already is never asked where it lives`() {
    // An update over an install made before the question: Home must keep showing that install.
    assertEquals(Where.PHONE, Place.answer(stored = null, installedHere = true))
  }

  @Test
  fun `a new phone is asked`() {
    assertNull(Place.answer(stored = null, installedHere = false))
  }

  @Test
  fun `an answer given wins over what is on disk`() {
    assertEquals(Where.REMOTE, Place.answer(stored = "REMOTE", installedHere = true))
    assertEquals(Where.PHONE, Place.answer(stored = "PHONE", installedHere = false))
  }

  @Test
  fun `an answer this version does not know is no answer`() {
    assertNull(Place.answer(stored = "MOON", installedHere = false))
    assertEquals(Where.PHONE, Place.answer(stored = "MOON", installedHere = true))
  }
}
