package tech.nouverse.nouride.android.install

import java.io.EOFException
import java.io.IOException
import java.io.InputStream

class TarEntry(
  val path: String,
  val type: Type,
  val mode: Int,
  val size: Long,
  val linkTarget: String,
  val mtimeSeconds: Long,
) {
  enum class Type { FILE, DIRECTORY, SYMLINK, HARDLINK, OTHER }
}

/**
 * Reads a tar stream: ustar, GNU long names and links, and pax `path`, `linkpath` and `size`,
 * which is what an OCI layer (Docker's Debian image) is written in.
 */
class TarReader(private val input: InputStream) {
  private var remaining = 0L
  private var padding = 0L

  /** The next entry, or null at the end of the archive. Skips what is left of the previous one. */
  fun next(): TarEntry? {
    skipFully(remaining + padding)
    remaining = 0
    padding = 0
    var longName: String? = null
    var longLink: String? = null
    var pax = emptyMap<String, String>()
    while (true) {
      val header = readBlock() ?: return null
      if (header.all { it == 0.toByte() }) return null
      val typeFlag = header[156].toInt().toChar()
      val size = number(header, 124, 12)
      when (typeFlag) {
        'L' -> { longName = cString(readData(size)); continue }
        'K' -> { longLink = cString(readData(size)); continue }
        'x' -> { pax = parsePax(readData(size)); continue }
        'g' -> { readData(size); continue }
      }
      val entrySize = pax["size"]?.toLong() ?: size
      val type = when (typeFlag) {
        '0', '\u0000', '7' -> TarEntry.Type.FILE
        '5' -> TarEntry.Type.DIRECTORY
        '2' -> TarEntry.Type.SYMLINK
        '1' -> TarEntry.Type.HARDLINK
        else -> TarEntry.Type.OTHER
      }
      remaining = entrySize
      padding = paddingOf(entrySize)
      return TarEntry(
        path = pax["path"] ?: longName ?: ustarName(header),
        type = type,
        mode = number(header, 100, 8).toInt(),
        size = entrySize,
        linkTarget = pax["linkpath"] ?: longLink ?: cString(header, 157, 100),
        mtimeSeconds = pax["mtime"]?.substringBefore('.')?.toLongOrNull() ?: number(header, 136, 12),
      )
    }
  }

  /** The current entry's data, which must be read before [next] is called again to keep it. */
  fun data(): InputStream = object : InputStream() {
    override fun read(): Int {
      if (remaining <= 0) return -1
      val b = input.read()
      if (b < 0) throw EOFException("tar entry is cut short")
      remaining--
      return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
      if (remaining <= 0) return -1
      val n = input.read(b, off, minOf(len.toLong(), remaining).toInt())
      if (n < 0) throw EOFException("tar entry is cut short")
      remaining -= n
      return n
    }
  }

  private fun readBlock(): ByteArray? {
    val block = ByteArray(BLOCK)
    var read = 0
    while (read < BLOCK) {
      val n = input.read(block, read, BLOCK - read)
      if (n < 0) {
        if (read == 0) return null
        throw EOFException("tar header is cut short")
      }
      read += n
    }
    return block
  }

  private fun readData(size: Long): ByteArray {
    if (size > MAX_META) throw IOException("tar metadata entry of $size bytes")
    val data = ByteArray(size.toInt())
    var read = 0
    while (read < data.size) {
      val n = input.read(data, read, data.size - read)
      if (n < 0) throw EOFException("tar metadata is cut short")
      read += n
    }
    skipFully(paddingOf(size))
    return data
  }

  private fun skipFully(count: Long) {
    var left = count
    val buffer = ByteArray(BLOCK * 16)
    while (left > 0) {
      val n = input.read(buffer, 0, minOf(left, buffer.size.toLong()).toInt())
      if (n < 0) throw EOFException("tar stream is cut short")
      left -= n
    }
  }

  private fun ustarName(header: ByteArray): String {
    val name = cString(header, 0, 100)
    val magic = String(header, 257, 6, Charsets.US_ASCII)
    // POSIX ustar splits long paths into prefix and name; GNU tar uses that area otherwise.
    if (magic == "ustar\u0000") {
      val prefix = cString(header, 345, 155)
      if (prefix.isNotEmpty()) return "$prefix/$name"
    }
    return name
  }

  companion object {
    private const val BLOCK = 512
    private const val MAX_META = 1L shl 20

    private fun paddingOf(size: Long) = (BLOCK - size % BLOCK) % BLOCK

    private fun cString(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): String {
      var end = offset
      while (end < offset + length && bytes[end] != 0.toByte()) end++
      return String(bytes, offset, end - offset, Charsets.UTF_8)
    }

    /** An octal field, or GNU's base-256 form when the high bit of the first byte is set. */
    private fun number(bytes: ByteArray, offset: Int, length: Int): Long {
      if (bytes[offset].toInt() and 0x80 != 0) {
        var value = (bytes[offset].toLong() and 0x7f)
        for (i in offset + 1 until offset + length) value = (value shl 8) or (bytes[i].toLong() and 0xff)
        return value
      }
      val text = cString(bytes, offset, length).trim()
      return if (text.isEmpty()) 0 else text.toLong(8)
    }

    /** Records of the form "<length> <key>=<value>\n". */
    internal fun parsePax(data: ByteArray): Map<String, String> {
      val result = mutableMapOf<String, String>()
      var pos = 0
      while (pos < data.size) {
        var space = pos
        while (space < data.size && data[space] != ' '.code.toByte()) space++
        if (space >= data.size) break
        val length = String(data, pos, space - pos, Charsets.US_ASCII).toIntOrNull() ?: break
        if (length <= 0 || pos + length > data.size) break
        val record = String(data, space + 1, pos + length - space - 2, Charsets.UTF_8)
        val eq = record.indexOf('=')
        if (eq > 0) result[record.substring(0, eq)] = record.substring(eq + 1)
        pos += length
      }
      return result
    }
  }
}
