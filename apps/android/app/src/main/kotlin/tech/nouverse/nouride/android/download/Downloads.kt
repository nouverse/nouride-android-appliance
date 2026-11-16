package tech.nouverse.nouride.android.download

import java.io.ByteArrayOutputStream
import java.net.URI

/**
 * A file the dashboard hands its screen to keep (System → Files, a chat's attachment, "Download all"): whether it is
 * kept, the name it is saved under in Downloads, and the number a second copy gets. `spec/downloads.md`, whose cases
 * the desktop's `downloads` crate passes too. Pure, so every name a server may send is tested without a phone.
 */
object Downloads {
  enum class Source {
    /** Nouride's own file, or a `blob:` the dashboard made: saved in Downloads. */
    SAVE,

    /** Another machine's: the browser's to fetch, so the dashboard's session never goes there. */
    OUTSIDE,

    /** Nothing to keep: a `blob:` of another origin, `data:`, `file:`, `javascript:`. */
    REFUSED,
  }

  /** The name of a file when nothing else names it. */
  const val FALLBACK = "download"

  /** Long enough for any name a person gives a file; a server's longer one is cut before its extension. */
  private const val MAX_CHARS = 200

  /** Refused in a name by the phone's shared storage and other systems: each becomes `_`. */
  private const val UNSAFE = "/\\:*?\"<>|"

  /** The compressions a `.tar` is wrapped in: `logs.tar.gz` is numbered as `logs (1).tar.gz`. */
  private val TAR_WRAPS = setOf("gz", "bz2", "xz", "zst")

  /** Whether [url] is kept, sent to the browser or refused, for the dashboard at [origin]. */
  fun source(url: String, origin: String): Source {
    val target = parse(url) ?: return Source.REFUSED
    val home = parse(origin)?.let(::originOf) ?: return Source.REFUSED
    return when (target.scheme?.lowercase()) {
      "http", "https" -> if (originOf(target) == home) Source.SAVE else Source.OUTSIDE
      // A blob's address is the origin that made it, then its id.
      "blob" -> if (parse(target.schemeSpecificPart)?.let(::originOf) == home) Source.SAVE else Source.REFUSED
      else -> Source.REFUSED
    }
  }

  /** The name a download is saved under: the page's own name for it, the server's, the URL's, or [FALLBACK], made safe. */
  fun name(given: String?, disposition: String?, url: String): String {
    val picked = given?.takeIf { it.isNotEmpty() } ?: disposition?.let(::fromDisposition) ?: fromUrl(url) ?: ""
    return safe(picked)
  }

  /** The file name in a `Content-Disposition`: its `filename*` (RFC 5987, UTF-8) first, then its `filename`. */
  fun fromDisposition(value: String): String? {
    val params = params(value)
    val star = params.firstOrNull { it.first == "filename*" }?.second?.let { v ->
      val parts = v.split('\'', limit = 3)
      if (parts.size == 3 && parts[0].equals("utf-8", ignoreCase = true)) percentDecode(parts[2]) else null
    }
    return (star ?: params.firstOrNull { it.first == "filename" }?.second)?.takeIf { it.isNotEmpty() }
  }

  /** [name] as a file of its own in Downloads: never a path, never hidden, nothing a file system refuses. */
  fun safe(name: String): String {
    val cleaned = StringBuilder()
    name.codePoints().forEach { cp ->
      when {
        Character.isISOControl(cp) -> Unit
        cp < 0x80 && cp.toChar() in UNSAFE -> cleaned.append('_')
        else -> cleaned.appendCodePoint(cp)
      }
    }
    val trimmed = cleaned.toString().trim { it == '.' || it.isWhitespace() }
    if (trimmed.isEmpty()) return FALLBACK
    if (trimmed.codePointCount(0, trimmed.length) <= MAX_CHARS) return trimmed
    val (stem, ext) = splitExtension(trimmed)
    val keep = (MAX_CHARS - ext.codePointCount(0, ext.length)).coerceAtLeast(0)
    return stem.substring(0, stem.offsetByCodePoints(0, minOf(keep, stem.codePointCount(0, stem.length)))) + ext
  }

  /** The [n]th copy of [name]: `report (1).pdf`. */
  fun numbered(name: String, n: Int): String {
    val (stem, ext) = splitExtension(name)
    return "$stem ($n)$ext"
  }

  /** [name], or its first numbered copy that [taken] says is free. */
  fun free(name: String, taken: (String) -> Boolean): String {
    if (!taken(name)) return name
    return generateSequence(1) { it + 1 }.map { numbered(name, it) }.first { !taken(it) }
  }

  /** The last part of an http(s) URL's path, percent-decoded. A `blob:`'s is a random id, never a name. */
  private fun fromUrl(url: String): String? {
    val uri = parse(url) ?: return null
    if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
    val last = uri.rawPath?.substringAfterLast('/').orEmpty()
    return if (last.isEmpty()) null else percentDecode(last)
  }

  /** The name before its extension, and the extension with its dot (`.tar.gz` as one), or empty. */
  private fun splitExtension(name: String): Pair<String, String> {
    val dot = name.lastIndexOf('.')
    if (dot <= 0) return name to ""
    val stem = name.substring(0, dot)
    if (name.substring(dot + 1) in TAR_WRAPS && stem.endsWith(".tar") && stem.length > ".tar".length) {
      val tar = stem.length - ".tar".length
      return name.substring(0, tar) to name.substring(tar)
    }
    return stem to name.substring(dot)
  }

  /** A header's `; key=value` parameters, keys lower-cased, a quoted value read with its `\` escapes and its `;`. */
  private fun params(value: String): List<Pair<String, String>> {
    val out = mutableListOf<Pair<String, String>>()
    var i = value.indexOf(';').takeIf { it >= 0 }?.plus(1) ?: return out
    while (i < value.length) {
      val keyEnd = value.indexOfAny(charArrayOf('=', ';'), i).let { if (it < 0) value.length else it }
      val key = value.substring(i, keyEnd).trim().lowercase()
      i = keyEnd + 1
      if (keyEnd < value.length && value[keyEnd] == ';') {
        if (key.isNotEmpty()) out += key to ""
        continue
      }
      while (i < value.length && value[i].isWhitespace()) i++
      val v = StringBuilder()
      if (i < value.length && value[i] == '"') {
        i++
        while (i < value.length) {
          val c = value[i++]
          if (c == '\\') { if (i < value.length) v.append(value[i++]) } else if (c == '"') break else v.append(c)
        }
        val next = value.indexOf(';', i)
        i = if (next < 0) value.length else next + 1
      } else {
        val next = value.indexOf(';', i).let { if (it < 0) value.length else it }
        v.append(value.substring(minOf(i, value.length), next).trim())
        i = next + 1
      }
      if (key.isNotEmpty()) out += key to v.toString()
    }
    return out
  }

  /** `%xx` read as UTF-8 bytes; a `%` not followed by two hex digits, or bytes that are not UTF-8, kept as written. */
  private fun percentDecode(text: String): String {
    val bytes = text.toByteArray(Charsets.UTF_8)
    val out = ByteArrayOutputStream(bytes.size)
    var i = 0
    while (i < bytes.size) {
      val b = bytes[i]
      if (b == '%'.code.toByte() && i + 2 < bytes.size) {
        val h = Character.digit(bytes[i + 1].toInt(), 16)
        val l = Character.digit(bytes[i + 2].toInt(), 16)
        if (h >= 0 && l >= 0) {
          out.write(h * 16 + l)
          i += 3
          continue
        }
      }
      out.write(b.toInt())
      i++
    }
    val decoded = out.toByteArray()
    val strict = Charsets.UTF_8.newDecoder()
    return try {
      strict.decode(java.nio.ByteBuffer.wrap(decoded)).toString()
    } catch (e: java.nio.charset.CharacterCodingException) {
      text
    }
  }

  /** Scheme, host in any case, and port, the scheme's own written or not: how a browser tells two origins apart. */
  private fun originOf(uri: URI): String? {
    val scheme = uri.scheme?.lowercase() ?: return null
    val host = uri.host?.lowercase() ?: return null
    val port = when {
      uri.port != -1 -> uri.port
      scheme == "https" -> 443
      scheme == "http" -> 80
      else -> return null
    }
    return "$scheme://$host:$port"
  }

  private fun parse(text: String): URI? = try {
    URI(text)
  } catch (e: Exception) {
    null
  }
}
