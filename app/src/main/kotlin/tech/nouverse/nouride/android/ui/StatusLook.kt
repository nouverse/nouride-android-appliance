package tech.nouverse.nouride.android.ui

import tech.nouverse.nouride.android.server.ServerState.Phase

/**
 * The cat's pose, each meaning what it means in the dashboard (Nouride's design rules): waving to greet, and to say
 * goodbye while turning off; riding its scooter while something is on its way; a thumbs up when it went well; sad
 * beside an unplugged cable when something failed or there is no network.
 */
enum class Mascot { HELLO, RIDE, THUMBS, UNPLUGGED }

/** The colour of the card around the cat. */
enum class Tone { CALM, BUSY, GOOD, WARN }

/** The one big button, or none while the app is busy and there is nothing to press. */
enum class Action { START, OPEN, RETRY, NONE }

/** Which sentence the screen says. Each has a title and a body in `strings.xml`. */
enum class Words { OFF, SETTING_UP, STARTING, WAKING, RUNNING, RESTARTING, STOPPING, FAILED }

data class Look(val mascot: Mascot, val tone: Tone, val words: Words, val action: Action, val busy: Boolean)

/**
 * What a person sees for each state of the server. The phases are the service's own; a person never reads one: the
 * users are not technical, so each phase becomes a sentence, a pose and at most one button. Pure, so a new phase
 * cannot ship without its words: the `when` below does not compile until it has them.
 */
object StatusLook {
  fun of(phase: Phase, answering: Boolean): Look = when (phase) {
    Phase.STOPPED -> Look(Mascot.HELLO, Tone.CALM, Words.OFF, Action.START, busy = false)
    Phase.EXTRACTING, Phase.INSTALLING -> Look(Mascot.RIDE, Tone.BUSY, Words.SETTING_UP, Action.NONE, busy = true)
    Phase.STARTING -> Look(Mascot.RIDE, Tone.BUSY, Words.STARTING, Action.NONE, busy = true)
    // Running is only "ready" once Nouride answers: the process can be up before its web server is.
    Phase.RUNNING ->
      if (answering) Look(Mascot.THUMBS, Tone.GOOD, Words.RUNNING, Action.OPEN, busy = false)
      else Look(Mascot.RIDE, Tone.BUSY, Words.WAKING, Action.NONE, busy = true)
    Phase.RESTARTING -> Look(Mascot.RIDE, Tone.BUSY, Words.RESTARTING, Action.NONE, busy = true)
    Phase.STOPPING -> Look(Mascot.HELLO, Tone.CALM, Words.STOPPING, Action.NONE, busy = true)
    Phase.FAILED -> Look(Mascot.UNPLUGGED, Tone.WARN, Words.FAILED, Action.RETRY, busy = false)
  }

  /** Whether "Turn off" is offered: whenever Nouride is on or on its way, never when it is off already. */
  fun canTurnOff(phase: Phase): Boolean = phase != Phase.STOPPED && phase != Phase.STOPPING && phase != Phase.FAILED

  /** Whether "Use from another device" is offered: only when there is something to reach. */
  fun canShare(look: Look): Boolean = look.action == Action.OPEN
}
