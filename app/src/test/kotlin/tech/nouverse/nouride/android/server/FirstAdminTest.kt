package tech.nouverse.nouride.android.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstAdminTest {
  private val installed = "PORT=18254\nHOST=0.0.0.0\n"

  @Test
  fun `the account goes into the env, and every line already there stays`() {
    val env = FirstAdmin.withAccount(installed, "abc123abc123abc123abc123")
    assertEquals(
      "PORT=18254\nHOST=0.0.0.0\nINITIAL_ADMIN_USERNAME=admin\nINITIAL_ADMIN_PASSWORD=abc123abc123abc123abc123\n",
      env,
    )
    // A file without a last newline does not glue the account to its last line.
    assertTrue(FirstAdmin.withAccount("PORT=18254", "p").startsWith("PORT=18254\nINITIAL_ADMIN_USERNAME=admin\n"))
  }

  @Test
  fun `a second start replaces the account rather than adding another`() {
    val twice = FirstAdmin.withAccount(FirstAdmin.withAccount(installed, "first"), "second")
    assertEquals(1, Regex("INITIAL_ADMIN_PASSWORD").findAll(twice).count())
    assertTrue(twice.contains("INITIAL_ADMIN_PASSWORD=second\n"))
  }

  @Test
  fun `once the daemon is ready, the password leaves the file and nothing else does`() {
    val withAccount = FirstAdmin.withAccount(installed, "secret-password-value")
    val after = FirstAdmin.withoutAccount(withAccount)
    assertEquals(installed, after)
    assertTrue(!after.contains("secret-password-value"))
  }

  @Test
  fun `passwords are long, hex, and not the same twice`() {
    val one = FirstAdmin.newPassword()
    assertTrue(one, Regex("[0-9a-f]{24}").matches(one))
    assertNotEquals(one, FirstAdmin.newPassword())
  }

  @Test
  fun `the session cookie is found among a sign-in's headers`() {
    val cookie = FirstAdmin.sessionCookie(
      listOf("other=1; Path=/", "nouride_session=tok%2Ben12345; Path=/; HttpOnly; SameSite=Lax; Max-Age=2592000"),
    )
    assertEquals("nouride_session=tok%2Ben12345", cookie)
    assertNull(FirstAdmin.sessionCookie(listOf("nouride_session=; Max-Age=0")))
    assertNull(FirstAdmin.sessionCookie(emptyList()))
  }
}
