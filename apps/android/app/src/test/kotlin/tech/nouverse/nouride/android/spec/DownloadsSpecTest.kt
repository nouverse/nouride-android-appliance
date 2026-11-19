package tech.nouverse.nouride.android.spec

import org.junit.Assert.assertEquals
import org.junit.Test
import tech.nouverse.nouride.android.download.Downloads

class DownloadsSpecTest {
  private val spec = Spec.fixture("downloads.json")

  @Test
  fun `which downloads are kept as the spec says`() {
    val wrong = Spec.cases(spec, "source").mapNotNull { case ->
      val got = Downloads.source(case.getString("url"), case.getString("origin")).name.lowercase()
      if (got == Spec.expected(case)) null else "$case: got $got"
    }
    assertEquals(emptyList<String>(), wrong)
  }

  @Test
  fun `every name as the spec says`() {
    val wrong = Spec.cases(spec, "name").mapNotNull { case ->
      val got = Downloads.name(case.optString("given", null), case.optString("disposition", null), case.getString("url"))
      if (got == Spec.expected(case)) null else "$case: got $got"
    }
    assertEquals(emptyList<String>(), wrong)
  }

  @Test
  fun `a second copy is numbered as the spec says`() {
    for (case in Spec.cases(spec, "numbered")) {
      assertEquals(case.toString(), Spec.expected(case), Downloads.numbered(case.getString("name"), case.getInt("n")))
    }
    for (case in Spec.cases(spec, "free")) {
      val taken = Spec.strings(case.getJSONArray("taken"))
      assertEquals(case.toString(), Spec.expected(case), Downloads.free(case.getString("name")) { it in taken })
    }
  }
}
