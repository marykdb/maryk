package maryk.core.processors.datastore

import maryk.core.extensions.bytes.SIGN_BYTE
import maryk.lib.exceptions.ParseException

fun findByteIndexAndSizeByPartIndex(
    partIndex: Int,
    indexable: ByteArray,
    keySize: Int,
    sourceStart: Int = 0,
    sourceEnd: Int = indexable.size,
    indexPartCount: Int = partIndex + 1
): Pair<Int, Int> {
    val sourceLength = sourceEnd - keySize - sourceStart

    fun resolvePartSizes(partCount: Int): IntArray? {
        val partSizes = IntArray(partCount)

        fun decodeLengths(endExclusive: Int, decodedCount: Int, valueBytesTotal: Int): Boolean {
            if (decodedCount == partCount) {
                return endExclusive == valueBytesTotal
            }

            val maxEncodedLength = minOf(5, endExclusive)
            for (encodedLength in 1..maxEncodedLength) {
                val start = endExclusive - encodedLength
                val absoluteStart = sourceStart + start
                val absoluteEnd = sourceStart + endExclusive
                if (!isValidVarIntWindow(indexable, absoluteStart, absoluteEnd)) continue

                val decodedValue = decodeVarIntWindow(indexable, absoluteStart, absoluteEnd)
                val nextValueBytesTotal = valueBytesTotal + decodedValue
                if (nextValueBytesTotal > start) continue

                partSizes[decodedCount] = decodedValue
                if (decodeLengths(start, decodedCount + 1, nextValueBytesTotal)) {
                    return true
                }
            }

            return false
        }

        return partSizes.takeIf { decodeLengths(sourceLength, 0, 0) }
    }

    val partSizes = resolvePartSizes(indexPartCount)
        ?: throw ParseException("Could not resolve index part lengths from trailing var ints")

    var indexForPart = 0
    repeat(partIndex) { indexForPart += partSizes[it] }

    return indexForPart to partSizes[partIndex]
}

fun findByteIndexByPartIndex(
    partIndex: Int,
    indexable: ByteArray,
    keySize: Int,
    sourceStart: Int = 0,
    sourceEnd: Int = indexable.size,
    indexPartCount: Int = partIndex + 1
): Int {
    return findByteIndexAndSizeByPartIndex(partIndex, indexable, keySize, sourceStart, sourceEnd, indexPartCount).first
}

private fun isValidVarIntWindow(bytes: ByteArray, start: Int, endExclusive: Int): Boolean {
    if (start >= endExclusive) return false
    if (bytes[endExclusive - 1].toInt() and SIGN_BYTE.toInt() != 0) return false

    for (index in start until endExclusive - 1) {
        if (bytes[index].toInt() and SIGN_BYTE.toInt() == 0) return false
    }

    return true
}

private fun decodeVarIntWindow(bytes: ByteArray, start: Int, endExclusive: Int): Int {
    var shift = 0
    var result = 0

    for (index in start until endExclusive) {
        val byte = bytes[index].toInt() and 0xFF
        if (shift == 28 && (byte and 0xF0) != 0) {
            throw ParseException("Malformed varInt")
        }
        result = result or ((byte and 0x7F) shl shift)
        shift += 7
    }

    return result
}
