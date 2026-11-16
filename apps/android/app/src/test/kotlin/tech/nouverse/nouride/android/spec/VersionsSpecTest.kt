package tech.nouverse.nouride.android.spec

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test
import tech.nouverse.nouride.android.update.UpdateFeed

class VersionsSpecTest {
  private val spec = Spec.fixture("versions.json")

  @Test
  fun `versions parse as the spec says`() {
    for (case in Spec.cases(spec, "parse")) {
      val want = (case.get("expect") as? JSONArray)?.let { a -> (0 until a.length()).map { a.getInt(it) } }
      assertEquals(case.getString("version"), want, UpdateFeed.parseVersion(case.getString("version")))
    }
  }

  @Test
  fun `newer as the spec says`() {
    for (case in Spec.cases(spec, "newer")) {
      val (candidate, installed) = case.getString("candidate") to case.getString("installed")
      assertEquals("$candidate over $installed", case.getBoolean("expect"), UpdateFeed.newer(candidate, installed))
    }
  }
}
