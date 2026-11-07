package tech.nouverse.nouride.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.nouverse.nouride.android.server.ServerState.Phase

class StatusLookTest {
  @Test
  fun `off offers Start, with the cat saying hello`() {
    val look = StatusLook.of(Phase.STOPPED, answering = false)
    assertEquals(Words.OFF, look.words)
    assertEquals(Action.START, look.action)
    assertEquals(Mascot.HELLO, look.mascot)
    assertFalse(look.busy)
  }

  @Test
  fun `running is ready only once Nouride answers`() {
    val ready = StatusLook.of(Phase.RUNNING, answering = true)
    assertEquals(Words.RUNNING, ready.words)
    assertEquals(Action.OPEN, ready.action)
    assertEquals(Tone.GOOD, ready.tone)
    // Up but not answering yet: no Open button that would show an error page.
    val waking = StatusLook.of(Phase.RUNNING, answering = false)
    assertEquals(Words.WAKING, waking.words)
    assertEquals(Action.NONE, waking.action)
    assertTrue(waking.busy)
  }

  @Test
  fun `the first start is one sentence, both of its phases, with nothing to press`() {
    for (phase in listOf(Phase.EXTRACTING, Phase.INSTALLING)) {
      val look = StatusLook.of(phase, answering = false)
      assertEquals(Words.SETTING_UP, look.words)
      assertEquals(Action.NONE, look.action)
      assertTrue(look.busy)
    }
  }

  @Test
  fun `a failure offers Try again and never a phase name`() {
    val look = StatusLook.of(Phase.FAILED, answering = false)
    assertEquals(Words.FAILED, look.words)
    assertEquals(Action.RETRY, look.action)
    assertEquals(Tone.WARN, look.tone)
  }

  @Test
  fun `every phase has words, and only a busy one has no button`() {
    for (phase in Phase.values()) for (answering in listOf(true, false)) {
      val look = StatusLook.of(phase, answering)
      assertEquals("$phase busy vs button", look.busy, look.action == Action.NONE)
    }
  }

  @Test
  fun `Turn off is offered while on or starting, sharing only when ready`() {
    assertFalse(StatusLook.canTurnOff(Phase.STOPPED))
    assertFalse(StatusLook.canTurnOff(Phase.FAILED))
    assertTrue(StatusLook.canTurnOff(Phase.RUNNING))
    assertTrue(StatusLook.canTurnOff(Phase.INSTALLING))
    assertTrue(StatusLook.canShare(StatusLook.of(Phase.RUNNING, answering = true)))
    assertFalse(StatusLook.canShare(StatusLook.of(Phase.RUNNING, answering = false)))
  }
}
