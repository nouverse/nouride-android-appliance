package tech.nouverse.nouride.android.spec

import org.junit.Assert.assertEquals
import org.junit.Test
import tech.nouverse.nouride.android.ui.LinkPolicy

class LinkPolicySpecTest {
  private val spec = Spec.fixture("link-policy.json")

  @Test
  fun `the constants are the spec's`() {
    assertEquals(spec.getString("home"), LinkPolicy.HOME)
    assertEquals(spec.getString("page_back"), LinkPolicy.BACK)
    assertEquals(spec.getString("user_agent_mark"), LinkPolicy.USER_AGENT_MARK)
  }

  @Test
  fun `in app as the spec says`() {
    for (case in Spec.cases(spec, "in_app")) {
      assertEquals("$case", Spec.expected(case), LinkPolicy.inApp(case.getString("url"), case.getString("origin")))
    }
  }

  @Test
  fun `link as the spec says`() {
    for (case in Spec.cases(spec, "link")) {
      val got = LinkPolicy.link(case.getString("url"), case.getBoolean("main_frame"), case.getString("origin"))
      assertEquals("$case", Spec.expected(case), got.name.lowercase())
    }
  }

  @Test
  fun `back inside as the spec says`() {
    for (case in Spec.cases(spec, "back_inside")) {
      val got = LinkPolicy.backInside(Spec.strings(case.getJSONArray("history")), case.getInt("current"), case.getString("origin"))
      assertEquals("$case", Spec.expected(case), got)
    }
  }

  @Test
  fun `back as the spec says`() {
    for (case in Spec.cases(spec, "back")) {
      val got = LinkPolicy.back(
        Spec.strings(case.getJSONArray("history")), case.getInt("current"), case.getBoolean("dashboard_shown"), case.getString("origin"),
      )
      assertEquals("$case", Spec.expected(case), got.name.lowercase())
    }
  }

  @Test
  fun `leave as the spec says`() {
    for (case in Spec.cases(spec, "leave")) {
      assertEquals("$case", Spec.expected(case), LinkPolicy.leave(case.getBoolean("dashboard_shown")).name.lowercase())
    }
  }

  @Test
  fun `unreachable as the spec says`() {
    for (case in Spec.cases(spec, "unreachable")) {
      val got = LinkPolicy.unreachable(case.getBoolean("main_frame"), case.getString("url"), case.getString("origin"))
      assertEquals("$case", Spec.expected(case), got)
    }
  }
}
