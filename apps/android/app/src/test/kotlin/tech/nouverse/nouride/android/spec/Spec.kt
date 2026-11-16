package tech.nouverse.nouride.android.spec

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The behaviour both apps share, as the cases in the repository's `spec/fixtures/`, which the desktop's Rust tests read
 * too. Gradle passes the folder as `nouride.spec` (app/build.gradle.kts). A case's `android` answer, when there is one,
 * is where this app differs from the desktop today.
 */
object Spec {
  fun fixture(name: String): JSONObject {
    val dir = System.getProperty("nouride.spec") ?: error("nouride.spec is not set: run the tests through Gradle")
    return JSONObject(File(dir, "fixtures/$name").readText())
  }

  fun cases(fixture: JSONObject, key: String): List<JSONObject> {
    val all: JSONArray = fixture.getJSONArray(key)
    check(all.length() > 0) { "no cases under $key" }
    return (0 until all.length()).map { all.getJSONObject(it) }
  }

  fun expected(case: JSONObject): Any = if (case.has("android")) case.get("android") else case.get("expect")

  fun strings(array: JSONArray): List<String> = (0 until array.length()).map { array.getString(it) }
}
