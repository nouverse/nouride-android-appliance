package tech.nouverse.nouride.android.backup

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipInputStream

class BackupTest {
  private fun tree(): File {
    val root = Files.createTempDirectory("backup").toFile()
    for ((path, text) in listOf(
      "config.toml" to "[daemon]\n",
      "data/nouride.db" to "open",
      "data/secrets.enc" to "the store",
      "data/secrets.sealed" to "sealed",
      "workspace/shared/app/index.ts" to "x",
      "workspace/shared/app/node_modules/a/index.js" to "y",
    )) {
      File(root, path).apply { parentFile.mkdirs() }.writeText(text)
    }
    return root
  }

  @Test
  fun `the plan takes what goes in and never follows a link`() {
    val root = tree()
    val outside = Files.createTempDirectory("outside").toFile()
    File(outside, "private.txt").writeText("not Nouride's")
    Files.createSymbolicLink(File(root, "workspace/shared/elsewhere").toPath(), outside.toPath())
    val plan = Backup.plan(root)
    assertEquals(listOf("config.toml", "data/secrets.sealed", "workspace/shared/app/index.ts"), plan.files.map { it.name })
    assertEquals(9L + 6 + 1, plan.bytes)
    root.deleteRecursively()
    outside.deleteRecursively()
  }

  @Test
  fun `the zip holds the plan and the snapshot as the database`() {
    val root = tree()
    File(root, "config.toml").setLastModified(1_700_000_000_000L)
    val snapshot = File.createTempFile("snapshot", ".db").apply { writeText("snapshot") }
    val out = ByteArrayOutputStream()
    var heard = 0L
    Backup.write(Backup.plan(root), snapshot, out) { heard = it }
    val names = mutableListOf<String>()
    var database = ""
    var configTime = 0L
    ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
      while (true) {
        val entry = zip.nextEntry ?: break
        names += entry.name
        if (entry.name == ".nouride/config.toml") configTime = entry.time
        if (entry.name == Backup.DATABASE_ENTRY) database = zip.readBytes().decodeToString()
      }
    }
    assertEquals(listOf(".nouride/config.toml", ".nouride/data/secrets.sealed", ".nouride/workspace/shared/app/index.ts", ".nouride/data/nouride.db"), names)
    assertEquals("snapshot", database)
    assertEquals(16L + 8, heard)
    // Each file keeps its own time, not the zip's (to the two seconds a zip keeps).
    assertEquals(1_700_000_000_000L / 2000, configTime / 2000)
    root.deleteRecursively()
    snapshot.delete()
  }
}
