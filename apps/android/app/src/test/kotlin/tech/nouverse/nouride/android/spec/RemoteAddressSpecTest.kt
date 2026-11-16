package tech.nouverse.nouride.android.spec

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.nouverse.nouride.android.server.RemoteHost

class RemoteAddressSpecTest {
  private val spec = Spec.fixture("remote-address.json")

  private fun asMap(read: RemoteHost.Read): Map<String, String> = when (read) {
    is RemoteHost.Read.Address -> mapOf("read" to "address", "base" to read.base)
    RemoteHost.Read.Empty -> mapOf("read" to "empty")
    RemoteHost.Read.PlainHttp -> mapOf("read" to "plain_http")
    RemoteHost.Read.NotAnAddress -> mapOf("read" to "not_an_address")
  }

  private fun asMap(json: JSONObject): Map<String, String> = json.keys().asSequence().associateWith { json.getString(it) }

  @Test
  fun `every typed address reads as the spec says`() {
    val cases = Spec.cases(spec, "read")
    assertTrue(cases.size >= 20)
    val wrong = cases.mapNotNull { case ->
      val typed = case.getString("typed")
      val (want, got) = asMap(Spec.expected(case) as JSONObject) to asMap(RemoteHost.read(typed))
      if (want == got) null else "typed \"$typed\": want $want, got $got"
    }
    assertEquals(emptyList<String>(), wrong)
  }

  @Test
  fun `shown and dashboard as the spec says`() {
    for (case in Spec.cases(spec, "shown")) assertEquals(Spec.expected(case), RemoteHost.shown(case.getString("base")))
    for (case in Spec.cases(spec, "dashboard")) assertEquals(Spec.expected(case), RemoteHost.dashboard(case.getString("base")))
  }
}
