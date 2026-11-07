package tech.nouverse.nouride.android.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission
import java.util.zip.GZIPOutputStream

/** java.nio stands in for android.system.Os. */
object JvmFileOps : FileOps {
  override fun symlink(target: String, path: String) {
    Files.createSymbolicLink(Paths.get(path), Paths.get(target))
  }

  override fun chmod(path: String, mode: Int) {
    val perms = PosixFilePermission.values().filterIndexed { i, _ -> mode and (1 shl (8 - i)) != 0 }.toSet()
    Files.setPosixFilePermissions(Paths.get(path), perms)
  }

  override fun isSymlink(path: String) = Files.isSymbolicLink(Paths.get(path))
}

class RootfsExtractorTest {
  @get:Rule
  val tmp = TemporaryFolder()

  @Test
  fun `files, directories, symlinks, and hard links as copies`() {
    val tar = TarBuilder()
      .dir("usr/", "755")
      .dir("usr/bin/", "755")
      .file("usr/bin/perl", "#!perl\n", "755")
      .hardlink("usr/bin/perl5.40.1", "usr/bin/perl")
      .symlink("bin", "usr/bin")
      .file("etc/shadow", "root:*:\n", "640")
      .build()
    val root = tmp.newFolder("rootfs")
    val count = RootfsExtractor(root, JvmFileOps).extract(ByteArrayInputStream(gzip(tar)))

    assertEquals(6, count)
    assertEquals("#!perl\n", File(root, "usr/bin/perl5.40.1").readText())
    assertFalse(Files.isSymbolicLink(File(root, "usr/bin/perl5.40.1").toPath()))
    assertEquals("usr/bin", Files.readSymbolicLink(File(root, "bin").toPath()).toString())
    assertTrue(File(root, "usr/bin/perl").canExecute())
    assertEquals("rw-r-----", mode(File(root, "etc/shadow")))
  }

  @Test
  fun `GNU long names and pax paths`() {
    val long = "usr/share/doc/" + "a".repeat(120) + "/changelog"
    val pax = "usr/lib/" + "b".repeat(150) + "/x.so"
    val tar = TarBuilder().gnuLongName(long, "one").paxPath(pax, "two").build()
    val root = tmp.newFolder("rootfs")
    RootfsExtractor(root, JvmFileOps).extract(ByteArrayInputStream(gzip(tar)))
    assertEquals("one", File(root, long).readText())
    assertEquals("two", File(root, pax).readText())
  }

  @Test(expected = IOException::class)
  fun `an entry that climbs out of the rootfs is refused`() {
    val tar = TarBuilder().file("../escape", "x", "644").build()
    RootfsExtractor(tmp.newFolder("rootfs"), JvmFileOps).extract(ByteArrayInputStream(gzip(tar)))
  }

  @Test(expected = IOException::class)
  fun `an entry under a symlink is refused`() {
    val outside = tmp.newFolder("outside")
    val tar = TarBuilder().symlink("lib", outside.path).file("lib/evil", "x", "644").build()
    try {
      RootfsExtractor(tmp.newFolder("rootfs"), JvmFileOps).extract(ByteArrayInputStream(gzip(tar)))
    } finally {
      assertFalse(File(outside, "evil").exists())
    }
  }

  /** The real Debian layer, when scripts/build-rootfs.sh has fetched it. */
  @Test
  fun `the Debian rootfs the app ships`() {
    val asset = File("src/main/assets/rootfs.tgz")
    assumeTrue("run scripts/build-rootfs.sh first", asset.isFile)
    val root = tmp.newFolder("rootfs")
    val count = asset.inputStream().use { RootfsExtractor(root, JvmFileOps).extract(it) }
    assertTrue("entries: $count", count > 5_000)
    assertTrue(File(root, "usr/bin/apt-get").canExecute())
    assertEquals("usr/bin", Files.readSymbolicLink(File(root, "bin").toPath()).toString())
    assertEquals(File(root, "usr/bin/perl").length(), File(root, "usr/bin/perl5.40.1").length())
    assertTrue(File(root, "etc/debian_version").readText().startsWith("13."))

    RootfsSetup.apply(root, AndroidIds.parse(ID_OUTPUT), JvmFileOps)
    assertEquals(RootfsSetup.RESOLV_CONF, File(root, "etc/resolv.conf").readText())
    assertTrue(File(root, "etc/passwd").readText().contains("aid_u0_a304:x:10304:10304:"))
    assertTrue(File(root, "etc/group").readText().contains("aid_inet:x:3003:root,aid_u0_a304"))
    assertEquals("rwxrwxrwx", mode(File(root, "tmp")))
  }

  @Test
  fun `the app's ids as id prints them`() {
    val ids = AndroidIds.parse(ID_OUTPUT)!!
    assertEquals("u0_a304", ids.user)
    assertEquals(10304, ids.uid)
    assertEquals(listOf(10304 to "u0_a304", 3003 to "inet", 9997 to "everybody", 20304 to "u0_a304_cache", 50304 to "all_a304"), ids.groups)
  }

  private fun mode(file: File) = java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(file.toPath()))

  private fun gzip(bytes: ByteArray) = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()

  companion object {
    const val ID_OUTPUT = "uid=10304(u0_a304) gid=10304(u0_a304) groups=10304(u0_a304),3003(inet),9997(everybody)," +
      "20304(u0_a304_cache),50304(all_a304) context=u:r:untrusted_app_27:s0:c48,c257,c512,c768"
  }
}

/** Writes ustar entries, GNU long names and pax headers, as Go's archive/tar does. */
class TarBuilder {
  private val out = ByteArrayOutputStream()

  fun dir(name: String, mode: String) = entry(name, '5', mode, ByteArray(0))
  fun file(name: String, text: String, mode: String) = entry(name, '0', mode, text.toByteArray())
  fun symlink(name: String, target: String) = entry(name, '2', "777", ByteArray(0), link = target)
  fun hardlink(name: String, target: String) = entry(name, '1', "755", ByteArray(0), link = target)

  fun gnuLongName(name: String, text: String): TarBuilder {
    entry("././@LongLink", 'L', "644", (name + "\u0000").toByteArray())
    return entry(name.take(99), '0', "644", text.toByteArray())
  }

  fun paxPath(name: String, text: String): TarBuilder {
    var record = " path=$name\n"
    var length = record.length
    while ((length.toString() + record).length != length) length = (length.toString() + record).length
    entry("PaxHeaders/x", 'x', "644", (length.toString() + record).toByteArray())
    return entry(name.takeLast(99), '0', "644", text.toByteArray())
  }

  fun build(): ByteArray = out.toByteArray() + ByteArray(1024)

  private fun entry(name: String, type: Char, mode: String, data: ByteArray, link: String = ""): TarBuilder {
    val header = ByteArray(512)
    fun put(offset: Int, value: String) = value.toByteArray().copyInto(header, offset)
    put(0, name.take(100))
    put(100, mode.padStart(7, '0'))
    put(108, "0000000")
    put(116, "0000000")
    put(124, java.lang.Long.toOctalString(data.size.toLong()).padStart(11, '0'))
    put(136, "00000000000")
    header[156] = type.code.toByte()
    put(157, link)
    put(257, "ustar\u0000")
    put(263, "00")
    "        ".toByteArray().copyInto(header, 148)
    val sum = header.sumOf { it.toInt() and 0xff }
    put(148, java.lang.Long.toOctalString(sum.toLong()).padStart(6, '0') + "\u0000 ")
    out.write(header)
    out.write(data)
    out.write(ByteArray(((512 - data.size % 512) % 512)))
    return this
  }
}
