package info.jukov.player.util

fun md5(value: String): String {
    val bytes = value.encodeToByteArray()
    val bitLength = bytes.size.toLong() * 8
    val paddedSize = ((bytes.size + 9 + 63) / 64) * 64
    val data = ByteArray(paddedSize)
    bytes.copyInto(data)
    data[bytes.size] = 0x80.toByte()
    for (i in 0..7) data[paddedSize - 8 + i] = (bitLength ushr (8 * i)).toByte()
    var a0 = 0x67452301
    var b0 = 0xefcdab89.toInt()
    var c0 = 0x98badcfe.toInt()
    var d0 = 0x10325476
    val shifts = intArrayOf(7, 12, 17, 22, 5, 9, 14, 20, 4, 11, 16, 23, 6, 10, 15, 21)
    val constants = IntArray(64) { index ->
        (kotlin.math.abs(kotlin.math.sin(index + 1.0)) * 4294967296.0).toLong().toInt()
    }
    for (offset in data.indices step 64) {
        val words = IntArray(16) { index ->
            (0..3).sumOf { byteIndex ->
                (data[offset + index * 4 + byteIndex].toInt() and 0xff) shl (8 * byteIndex)
            }
        }
        var a = a0
        var b = b0
        var c = c0
        var d = d0
        for (i in 0 until 64) {
            val (f, g) = when (i) {
                in 0..15 -> ((b and c) or (b.inv() and d)) to i
                in 16..31 -> ((d and b) or (d.inv() and c)) to ((5 * i + 1) % 16)
                in 32..47 -> (b xor c xor d) to ((3 * i + 5) % 16)
                else -> (c xor (b or d.inv())) to ((7 * i) % 16)
            }
            val previousD = d
            d = c
            c = b
            val shift = shifts[(i / 16) * 4 + i % 4]
            b += (a + f + constants[i] + words[g]).rotateLeft(shift)
            a = previousD
        }
        a0 += a
        b0 += b
        c0 += c
        d0 += d
    }
    return intArrayOf(a0, b0, c0, d0)
        .flatMap { number -> (0..3).map { (number ushr (8 * it)).toByte() } }
        .toByteArray()
        .toHex()
}
