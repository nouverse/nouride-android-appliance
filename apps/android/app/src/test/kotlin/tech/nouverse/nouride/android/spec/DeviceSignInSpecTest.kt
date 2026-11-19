package tech.nouverse.nouride.android.spec

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.nouverse.nouride.android.signin.DeviceSignIn
import tech.nouverse.nouride.android.signin.DeviceSignIn.Answer
import tech.nouverse.nouride.android.signin.DeviceSignIn.Call
import tech.nouverse.nouride.android.signin.DeviceSignIn.Step

class DeviceSignInSpecTest {
  private val spec = Spec.fixture("device-sign-in.json")

  private fun expected(case: JSONObject): Any = Spec.expected(case)

  private fun stepJson(step: Step): String = when (step) {
    Step.SignedIn -> """{"do":"signed_in"}"""
    Step.Trade -> """{"do":"trade"}"""
    Step.Mint -> """{"do":"mint"}"""
    is Step.Wait -> """{"do":"wait","ms":${step.ms},"then":"${step.then.name.lowercase()}"}"""
    Step.GiveUp -> """{"do":"give_up"}"""
  }

  /** The same JSON, whatever the order of its keys. */
  private fun same(a: Any, b: String): Boolean = flat(JSONObject(a.toString())) == flat(JSONObject(b))

  private fun flat(o: JSONObject): Map<String, String> = o.keys().asSequence().associateWith { o.get(it).toString() }

  @Test
  fun `the label as the spec says, and always one Nouride takes`() {
    val wrong = Spec.cases(spec, "label").mapNotNull { case ->
      val got = DeviceSignIn.label(case.getString("name"))
      assertTrue(got, got.isNotEmpty() && got.length <= 60 && got.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it in " ._'()-" })
      if (got == expected(case)) null else "$case: got $got"
    }
    assertEquals(emptyList<String>(), wrong)
  }

  @Test
  fun `every answer leads where the spec says`() {
    val wrong = Spec.cases(spec, "answers").mapNotNull { case ->
      val call = if (case.getString("call") == "trade") Call.TRADE else Call.MINT
      val answer = Answer(
        code = case.getInt("code"),
        retryAfter = if (case.has("retry_after")) case.getString("retry_after") else null,
        cookie = if (case.optBoolean("cookie", true)) "nouride_session=s" else null,
        value = if (case.optBoolean("value", true)) "ndev_x" else null,
      )
      val got = stepJson(DeviceSignIn.next(call, answer, case.optBoolean("minted", false)))
      if (same(expected(case), got)) null else "$case: got $got"
    }
    assertEquals(emptyList<String>(), wrong)
  }

  /** Nouride as a list of answers, in order; what the round asked of it, as the fixture writes it. */
  private class Scripted(private val answers: MutableList<JSONObject>, var kept: String?) : DeviceSignIn.Effects {
    val calls = mutableListOf<String>()

    private fun answer(): Answer {
      check(answers.isNotEmpty()) { "the round asked more than the case answers: $calls" }
      val a = answers.removeAt(0)
      val code = a.getInt("code")
      return Answer(
        code = code,
        retryAfter = if (a.has("retry_after")) a.getString("retry_after") else null,
        cookie = if (code == 200) "nouride_session=s" else null,
        value = if (a.has("value")) a.getString("value") else null,
      )
    }

    fun left() = answers.size

    override fun trade(token: String): Answer {
      calls.add("trade $token")
      return answer()
    }

    override fun mint(): Answer {
      calls.add("mint")
      return answer()
    }

    override fun keep(token: String) {
      kept = token
    }

    override fun waitFor(ms: Long) {
      calls.add("wait $ms")
    }
  }

  @Test
  fun `whole rounds ask Nouride exactly what the spec says`() {
    for (case in Spec.cases(spec, "rounds")) {
      val about = case.getString("about")
      val kept = if (case.isNull("kept")) null else case.getString("kept")
      val answers = case.getJSONArray("answers").let { a -> (0 until a.length()).map { a.getJSONObject(it) }.toMutableList() }
      val nouride = Scripted(answers, kept)
      val outcome = DeviceSignIn.round(kept, nouride)
      val want = expected(case) as JSONObject
      assertEquals(about, Spec.strings(want.getJSONArray("calls")), nouride.calls)
      val got = when (outcome) {
        is DeviceSignIn.Outcome.SignedIn -> "signed_in".also { assertEquals("nouride_session=s", outcome.cookie) }
        is DeviceSignIn.Outcome.GaveUp -> "give_up"
      }
      assertEquals(about, want.getString("outcome"), got)
      assertEquals(about, if (want.isNull("kept")) null else want.getString("kept"), nouride.kept)
      assertEquals("$about: answers left over", 0, nouride.left())
    }
  }

  @Test
  fun `the sign-in page, where the window goes after it, and how often, as the spec says`() {
    val wrong = mutableListOf<String>()
    for (case in Spec.cases(spec, "sign_in_page")) {
      val got = DeviceSignIn.signInPage(case.getString("url"), case.getString("origin"))
      if (got != expected(case)) wrong.add("$case: got $got")
    }
    for (case in Spec.cases(spec, "after_sign_in")) {
      val got = DeviceSignIn.afterSignIn(case.getString("url"), case.getString("origin"))
      if (got != expected(case)) wrong.add("$case: got $got")
    }
    for (case in Spec.cases(spec, "gave_up_counts")) {
      val got = DeviceSignIn.gaveUpCounts(case.getInt("code"))
      if (got != expected(case)) wrong.add("$case: got $got")
    }
    for (case in Spec.cases(spec, "again")) {
      val got = DeviceSignIn.again(if (case.isNull("last")) null else case.getLong("last"), case.getLong("now"))
      if (got != expected(case)) wrong.add("$case: got $got")
    }
    assertEquals(emptyList<String>(), wrong)
  }

  @Test
  fun `the session cookie is found among a trade's headers`() {
    val cookie = DeviceSignIn.sessionCookie(
      listOf("other=1; Path=/", "nouride_session=tok%2Ben12345; Path=/; HttpOnly; SameSite=Lax; Max-Age=604800"),
    )
    assertEquals("nouride_session=tok%2Ben12345", cookie)
    assertNull(DeviceSignIn.sessionCookie(listOf("nouride_session=; Max-Age=0")))
    assertNull(DeviceSignIn.sessionCookie(emptyList()))
  }
}
