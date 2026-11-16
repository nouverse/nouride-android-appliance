package tech.nouverse.nouride.android.ui

import kotlin.math.abs

/**
 * The QR code on the Connect tab, so a tablet or another phone opens Nouride by pointing its camera instead of typing an
 * address. Written here because the app takes no third-party library: byte mode, error correction M (a code on a
 * screen survives glare and a smudge), versions 1 to 10 (up to 213 bytes, far more than an address), and the mask the
 * standard's penalty rules pick. Pure: `QrCodeTest` checks it module by module against an independent encoder.
 */
object QrCode {
  /** The modules, `[row][column]`, `true` for dark, without the quiet zone (whoever draws it adds four modules). */
  fun encode(text: String): Array<BooleanArray> = encode(text.toByteArray(Charsets.UTF_8), null, null).modules

  class Symbol(val version: Int, val mask: Int, val modules: Array<BooleanArray>)

  /** [version] and [mask] forced only by the tests, to compare every mask with the reference. */
  internal fun encode(data: ByteArray, version: Int?, mask: Int?): Symbol {
    val v = version ?: (1..MAX_VERSION).firstOrNull { fits(data.size, it) }
      ?: throw IllegalArgumentException("${data.size} bytes do not fit a version $MAX_VERSION QR code")
    require(fits(data.size, v)) { "${data.size} bytes do not fit version $v" }
    val codewords = interleave(v, dataCodewords(data, v))
    val grid = Grid(v)
    grid.place(codewords)
    // Each mask is scored before the format and version information go in, as ISO/IEC 18004:2015 7.8 has it.
    val chosen = mask ?: (0..7).minByOrNull { penalty(grid.masked(it, finished = false)) }!!
    return Symbol(v, chosen, grid.masked(chosen, finished = true))
  }

  private const val MAX_VERSION = 10

  /** Error correction codewords in each block, for level M, by version. */
  private val EC_PER_BLOCK = intArrayOf(0, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26)

  /** Blocks for level M: how many of the first size, its data codewords, how many of the second, its data codewords. */
  private val BLOCKS = arrayOf(
    intArrayOf(), intArrayOf(1, 16, 0, 0), intArrayOf(1, 28, 0, 0), intArrayOf(1, 44, 0, 0), intArrayOf(2, 32, 0, 0),
    intArrayOf(2, 43, 0, 0), intArrayOf(4, 27, 0, 0), intArrayOf(4, 31, 0, 0), intArrayOf(2, 38, 2, 39),
    intArrayOf(3, 36, 2, 37), intArrayOf(4, 43, 1, 44),
  )

  /** The centres of the alignment patterns, on both axes, by version. */
  private val ALIGNMENT = arrayOf(
    intArrayOf(), intArrayOf(), intArrayOf(6, 18), intArrayOf(6, 22), intArrayOf(6, 26), intArrayOf(6, 30),
    intArrayOf(6, 34), intArrayOf(6, 22, 38), intArrayOf(6, 24, 42), intArrayOf(6, 26, 46), intArrayOf(6, 28, 50),
  )

  private fun dataCapacity(v: Int): Int = BLOCKS[v].let { it[0] * it[1] + it[2] * it[3] }

  private fun countBits(v: Int): Int = if (v <= 9) 8 else 16

  private fun fits(bytes: Int, v: Int): Boolean = 4 + countBits(v) + 8 * bytes <= dataCapacity(v) * 8

  /** The mode, the length, the bytes, the terminator and the padding, as the data codewords. */
  private fun dataCodewords(data: ByteArray, v: Int): IntArray {
    val bits = ArrayList<Boolean>()
    fun put(value: Int, length: Int) = (length - 1 downTo 0).forEach { bits.add((value ushr it) and 1 == 1) }
    put(0b0100, 4)
    put(data.size, countBits(v))
    data.forEach { put(it.toInt() and 0xff, 8) }
    val capacity = dataCapacity(v) * 8
    put(0, minOf(4, capacity - bits.size))
    put(0, (8 - bits.size % 8) % 8)
    var pad = 0xEC
    while (bits.size < capacity) {
      put(pad, 8)
      pad = pad xor 0xEC xor 0x11
    }
    return IntArray(capacity / 8) { byte -> (0 until 8).fold(0) { acc, bit -> (acc shl 1) or (if (bits[byte * 8 + bit]) 1 else 0) } }
  }

  /** The data split into blocks, each followed by its error correction, interleaved column by column. */
  private fun interleave(v: Int, data: IntArray): IntArray {
    val (count1, size1, count2, size2) = BLOCKS[v].toList()
    val blocks = ArrayList<IntArray>()
    var at = 0
    repeat(count1) { blocks.add(data.copyOfRange(at, at + size1)); at += size1 }
    repeat(count2) { blocks.add(data.copyOfRange(at, at + size2)); at += size2 }
    val ec = blocks.map { reedSolomon(it, EC_PER_BLOCK[v]) }
    val out = ArrayList<Int>()
    for (i in 0 until maxOf(size1, size2)) blocks.forEach { if (i < it.size) out.add(it[i]) }
    for (i in 0 until EC_PER_BLOCK[v]) ec.forEach { out.add(it[i]) }
    return out.toIntArray()
  }

  /** Multiplication in GF(256) with the QR code's polynomial, x^8 + x^4 + x^3 + x^2 + 1. */
  private fun gfMultiply(a: Int, b: Int): Int {
    var product = 0
    var x = a
    var y = b
    while (y != 0) {
      if (y and 1 == 1) product = product xor x
      x = x shl 1
      if (x and 0x100 != 0) x = x xor 0x11D
      y = y ushr 1
    }
    return product
  }

  /** The remainder of the block, as a polynomial, divided by the generator with roots 2^0 .. 2^(degree - 1). */
  private fun reedSolomon(block: IntArray, degree: Int): IntArray {
    // The generator's coefficients after the leading 1, highest power first.
    val generator = IntArray(degree).also { it[degree - 1] = 1 }
    var root = 1
    repeat(degree) {
      for (j in 0 until degree) {
        generator[j] = gfMultiply(generator[j], root)
        if (j + 1 < degree) generator[j] = generator[j] xor generator[j + 1]
      }
      root = gfMultiply(root, 2)
    }
    val remainder = IntArray(degree)
    for (byte in block) {
      val factor = byte xor remainder[0]
      System.arraycopy(remainder, 1, remainder, 0, degree - 1)
      remainder[degree - 1] = 0
      for (j in 0 until degree) remainder[j] = remainder[j] xor gfMultiply(generator[j], factor)
    }
    return remainder
  }

  /** The symbol being built: its modules, and which of them are patterns rather than data. */
  private class Grid(val version: Int) {
    val size = 17 + 4 * version
    val modules = Array(size) { BooleanArray(size) }
    val reserved = Array(size) { BooleanArray(size) }

    init {
      for (i in 0 until size) {
        fixed(6, i, i % 2 == 0)
        fixed(i, 6, i % 2 == 0)
      }
      finder(3, 3)
      finder(3, size - 4)
      finder(size - 4, 3)
      val centres = ALIGNMENT[version]
      val last = centres.size - 1
      for (i in centres.indices) for (j in centres.indices) {
        // Not over the three finders.
        if ((i == 0 && j == 0) || (i == 0 && j == last) || (i == last && j == 0)) continue
        alignment(centres[i], centres[j])
      }
      // The format and version areas are kept for the end, light until the mask is known.
      formatPlaces().forEach { (row, column) -> fixed(row, column, false) }
      if (version >= 7) versionPlaces().forEach { (row, column, _) -> fixed(row, column, false) }
    }

    fun fixed(row: Int, column: Int, dark: Boolean) {
      modules[row][column] = dark
      reserved[row][column] = true
    }

    private fun finder(row: Int, column: Int) {
      for (dy in -4..4) for (dx in -4..4) {
        val r = row + dy
        val c = column + dx
        if (r !in 0 until size || c !in 0 until size) continue
        val ring = maxOf(abs(dx), abs(dy))
        fixed(r, c, ring != 2 && ring != 4)
      }
    }

    private fun alignment(row: Int, column: Int) {
      for (dy in -2..2) for (dx in -2..2) fixed(row + dy, column + dx, maxOf(abs(dx), abs(dy)) != 1)
    }

    /** Where the format information goes, bit 0 to 14 of each copy, then the dark module. */
    private fun formatPlaces(): List<Pair<Int, Int>> =
      (0..5).map { it to 8 } + listOf(7 to 8, 8 to 8, 8 to 7) + (9..14).map { 8 to 14 - it } +
        (0..7).map { 8 to size - 1 - it } + (8..14).map { size - 15 + it to 8 } + listOf(size - 8 to 8)

    /** Level M's two bits (00) and the mask, BCH-coded, in both copies, and the dark module after them. */
    private fun drawFormat(into: Array<BooleanArray>, mask: Int) {
      var remainder = mask
      repeat(10) { remainder = (remainder shl 1) xor ((remainder ushr 9) * 0x537) }
      val bits = ((mask shl 10) or remainder) xor 0x5412
      formatPlaces().forEachIndexed { i, (row, column) -> into[row][column] = i >= 30 || (bits ushr (i % 15)) and 1 == 1 }
    }

    /** Where the version information goes (version 7 and up), each with its value. */
    private fun versionPlaces(): List<Triple<Int, Int, Boolean>> {
      var remainder = version
      repeat(12) { remainder = (remainder shl 1) xor ((remainder ushr 11) * 0x1F25) }
      val bits = (version shl 12) or remainder
      return (0 until 18).flatMap { i ->
        val dark = (bits ushr i) and 1 == 1
        val a = size - 11 + i % 3
        val b = i / 3
        listOf(Triple(b, a, dark), Triple(a, b, dark))
      }
    }

    /** The codewords' bits up and down the two-module columns, right to left, around the patterns. */
    fun place(codewords: IntArray) {
      var i = 0
      var right = size - 1
      while (right >= 1) {
        if (right == 6) right = 5
        for (vertical in 0 until size) for (j in 0..1) {
          val column = right - j
          val upward = (right + 1) and 2 == 0
          val row = if (upward) size - 1 - vertical else vertical
          if (!reserved[row][column] && i < codewords.size * 8) {
            modules[row][column] = (codewords[i ushr 3] ushr (7 - (i and 7))) and 1 == 1
            i++
          }
        }
        right -= 2
      }
    }

    /** The data masked; [finished] adds the format and version information, as it is drawn. */
    fun masked(mask: Int, finished: Boolean): Array<BooleanArray> {
      val out = Array(size) { modules[it].copyOf() }
      for (row in 0 until size) for (column in 0 until size) {
        if (reserved[row][column]) continue
        val flip = when (mask) {
          0 -> (row + column) % 2 == 0
          1 -> row % 2 == 0
          2 -> column % 3 == 0
          3 -> (row + column) % 3 == 0
          4 -> (row / 2 + column / 3) % 2 == 0
          5 -> row * column % 2 + row * column % 3 == 0
          6 -> (row * column % 2 + row * column % 3) % 2 == 0
          else -> ((row + column) % 2 + row * column % 3) % 2 == 0
        }
        if (flip) out[row][column] = !out[row][column]
      }
      if (finished) {
        drawFormat(out, mask)
        if (version >= 7) versionPlaces().forEach { (row, column, dark) -> out[row][column] = dark }
      }
      return out
    }
  }

  /** The standard's four penalties for a masked symbol (ISO/IEC 18004:2015 7.8.3.1): the lowest picks the mask. */
  private fun penalty(modules: Array<BooleanArray>): Int {
    val size = modules.size
    var total = 0
    val lines = (0 until size).map { r -> BooleanArray(size) { modules[r][it] } } +
      (0 until size).map { c -> BooleanArray(size) { modules[it][c] } }
    for (line in lines) {
      // Rule 1: five or more of a colour in a row, 3 and one more for each beyond five.
      var run = 1
      for (i in 1..size) {
        if (i < size && line[i] == line[i - 1]) run++ else {
          if (run >= 5) total += run - 2
          run = 1
        }
      }
      total += finderLike(line)
    }
    // Rule 2: each 2 x 2 block of one colour.
    for (r in 0 until size - 1) for (c in 0 until size - 1) {
      val v = modules[r][c]
      if (modules[r][c + 1] == v && modules[r + 1][c] == v && modules[r + 1][c + 1] == v) total += 3
    }
    // Rule 4: how far the dark share is from half, in whole steps of 5 %.
    val dark = modules.sumOf { row -> row.count { it } }
    total += 10 * (abs(dark * 100.0 / (size * size) - 50) / 5).toInt()
    return total
  }

  private val FINDER_LIKE = "1011101".map { it == '1' }.toBooleanArray()

  /**
   * Rule 3: dark, light, three dark, light, dark, with light on one side for four modules, or for as far as the edge
   * is: each 40. A match that does not count is searched again from its third dark module.
   */
  private fun finderLike(line: BooleanArray): Int {
    val size = line.size
    var total = 0
    var from = 0
    while (true) {
      val at = (from..size - 7).firstOrNull { start -> FINDER_LIKE.indices.all { line[start + it] == FINDER_LIKE[it] } } ?: break
      val lightBefore = (maxOf(at - 4, 0) until at).none { line[it] }
      val lightAfter = (at + 7 until minOf(at + 11, size)).none { line[it] }
      if (at == 0 || at == size - 7 || lightBefore || lightAfter) {
        total += 40
        from = at + 7
      } else {
        from = at + 4
      }
    }
    return total
  }
}
