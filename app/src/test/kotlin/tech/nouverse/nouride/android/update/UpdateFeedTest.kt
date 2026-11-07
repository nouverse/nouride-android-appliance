package tech.nouverse.nouride.android.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateFeedTest {
  /** The shape of GitHub's `releases/latest` for a release the workflow published, trimmed to what is read. */
  private fun feed(tag: String = "v0.1.1", draft: Boolean = false, prerelease: Boolean = false, assets: String? = null) = """
    {"tag_name":"$tag","draft":$draft,"prerelease":$prerelease,"name":"Nouride Home ${tag.removePrefix("v")}",
     "assets":${assets ?: """[
       {"name":"nouride-home-${tag.removePrefix("v")}.apk","browser_download_url":"https://github.com/nouverse/nouride-android-appliance/releases/download/$tag/nouride-home-${tag.removePrefix("v")}.apk"},
       {"name":"nouride-home-${tag.removePrefix("v")}.apk.sha256","browser_download_url":"https://github.com/nouverse/nouride-android-appliance/releases/download/$tag/nouride-home-${tag.removePrefix("v")}.apk.sha256"}
     ]"""}}
  """.trimIndent()

  @Test
  fun `a published release gives its version, its APK and its sha256`() {
    val release = UpdateFeed.parse(feed())!!
    assertEquals("0.1.1", release.version)
    assertTrue(release.apkUrl.endsWith("/v0.1.1/nouride-home-0.1.1.apk"))
    assertTrue(release.sha256Url.endsWith("/v0.1.1/nouride-home-0.1.1.apk.sha256"))
  }

  @Test
  fun `a draft, a pre-release, an odd tag or a release without both files is no update`() {
    assertNull(UpdateFeed.parse(feed(draft = true)))
    assertNull(UpdateFeed.parse(feed(prerelease = true)))
    assertNull(UpdateFeed.parse(feed(tag = "v0.1")))
    assertNull(UpdateFeed.parse(feed(tag = "nightly")))
    assertNull(UpdateFeed.parse(feed(assets = """[{"name":"nouride-home-0.1.1.apk","browser_download_url":"https://x/a.apk"}]""")))
    // Another release's APK attached under this tag is not taken for this one.
    assertNull(UpdateFeed.parse(feed(assets = """[{"name":"nouride-home-0.1.0.apk","browser_download_url":"https://x/a"},{"name":"nouride-home-0.1.0.apk.sha256","browser_download_url":"https://x/b"}]""")))
    // Plain HTTP only from the phone itself, never from the network.
    assertNull(UpdateFeed.parse(feed(assets = """[{"name":"nouride-home-0.1.1.apk","browser_download_url":"http://evil.example/a.apk"},{"name":"nouride-home-0.1.1.apk.sha256","browser_download_url":"http://evil.example/a.sha256"}]""")))
    assertNull(UpdateFeed.parse("not json"))
    assertNull(UpdateFeed.parse("""{"message":"API rate limit exceeded"}"""))
  }

  @Test
  fun `only a later release is an update, and a developer's build is never offered one`() {
    assertTrue(UpdateFeed.newer("0.1.1", "0.1.0"))
    assertTrue(UpdateFeed.newer("0.2.0", "0.1.9"))
    assertTrue(UpdateFeed.newer("1.0.0", "0.10.3"))
    assertTrue(UpdateFeed.newer("0.10.0", "0.9.0"))
    assertFalse(UpdateFeed.newer("0.1.0", "0.1.0"))
    assertFalse(UpdateFeed.newer("0.0.9", "0.1.0"))
    assertFalse(UpdateFeed.newer("0.1.1", "0.0.0-dev"))
    assertFalse(UpdateFeed.newer("0.1.1-beta", "0.1.0"))
    assertFalse(UpdateFeed.newer("0.1.+1", "0.1.0"))
  }

  @Test
  fun `the sha256 is read from the release's sha256sum line for that file only`() {
    val hash = "a".repeat(64)
    assertEquals(hash, UpdateFeed.expectedSha256("$hash  nouride-home-0.1.1.apk\n", "nouride-home-0.1.1.apk"))
    assertEquals(hash, UpdateFeed.expectedSha256("${hash.uppercase()} *nouride-home-0.1.1.apk", "nouride-home-0.1.1.apk"))
    assertNull(UpdateFeed.expectedSha256("$hash  nouride-home-0.1.0.apk", "nouride-home-0.1.1.apk"))
    assertNull(UpdateFeed.expectedSha256("abc  nouride-home-0.1.1.apk", "nouride-home-0.1.1.apk"))
    assertNull(UpdateFeed.expectedSha256("", "nouride-home-0.1.1.apk"))
  }
}
