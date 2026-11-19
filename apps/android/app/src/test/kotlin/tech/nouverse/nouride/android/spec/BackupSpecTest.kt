package tech.nouverse.nouride.android.spec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.nouverse.nouride.android.backup.Backup

class BackupSpecTest {
  private val spec = Spec.fixture("backup.json")

  @Test
  fun `the app holds the spec's list of what stays out`() {
    assertEquals(Spec.cases(spec, "out").map { it.getString("pattern") }, Backup.OUT)
  }

  @Test
  fun `every path is in or out as the spec says`() {
    val cases = Spec.cases(spec, "kept")
    assertTrue(cases.size >= 60)
    val wrong = cases.mapNotNull { case ->
      val path = case.getString("path")
      val got = if (Backup.kept(path)) "in" else "out"
      if (got == Spec.expected(case)) null else "$path: want ${Spec.expected(case)}, got $got"
    }
    assertEquals(emptyList<String>(), wrong)
  }

  @Test
  fun `the name, the entries and the snapshot as the spec says`() {
    val entries = spec.getJSONObject("entries")
    assertEquals(entries.getString("folder"), Backup.FOLDER_ENTRY)
    assertEquals(entries.getString("database"), Backup.DATABASE_ENTRY)
    for (case in Spec.cases(spec, "zip_name")) assertEquals(Spec.expected(case), Backup.zipName(case.getString("date")))
    for (case in Spec.cases(spec, "snapshot")) {
      val want = Spec.expected(case).takeIf { it != org.json.JSONObject.NULL }
      assertEquals(case.getString("output"), want, Backup.snapshot(case.getString("output")))
    }
  }
}
