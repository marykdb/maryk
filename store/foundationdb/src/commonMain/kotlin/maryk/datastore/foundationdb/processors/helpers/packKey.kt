package maryk.datastore.foundationdb.processors.helpers

import maryk.lib.bytes.combineToByteArray

internal fun concatArrays(
    first: ByteArray,
    second: ByteArray
): ByteArray {
    val totalSize = first.size.checkedSizePlus(second.size)
    return ByteArray(totalSize).also {
        first.copyInto(it, 0)
        second.copyInto(it, first.size)
    }
}

internal fun concatArrays(
    first: ByteArray,
    second: ByteArray,
    third: ByteArray
): ByteArray {
    val firstTwoSize = first.size.checkedSizePlus(second.size)
    val totalSize = firstTwoSize.checkedSizePlus(third.size)
    return ByteArray(totalSize).also {
        first.copyInto(it, 0)
        second.copyInto(it, first.size)
        third.copyInto(it, firstTwoSize)
    }
}

internal fun concatArrays(
    first: ByteArray,
    second: ByteArray,
    third: ByteArray,
    fourth: ByteArray
): ByteArray {
    val firstTwoSize = first.size.checkedSizePlus(second.size)
    val firstThreeSize = firstTwoSize.checkedSizePlus(third.size)
    val totalSize = firstThreeSize.checkedSizePlus(fourth.size)
    return ByteArray(totalSize).also {
        first.copyInto(it, 0)
        second.copyInto(it, first.size)
        third.copyInto(it, firstTwoSize)
        fourth.copyInto(it, firstThreeSize)
    }
}

internal fun concatArrays(
    first: ByteArray,
    second: ByteArray,
    third: ByteArray,
    separator: Byte,
    fourth: ByteArray
): ByteArray {
    val firstThreeSize = first.size
        .checkedSizePlus(second.size)
        .checkedSizePlus(third.size)
    val withSeparatorSize = firstThreeSize.checkedSizePlus(1)
    val totalSize = withSeparatorSize.checkedSizePlus(fourth.size)
    return ByteArray(totalSize).also {
        first.copyInto(it, 0)
        second.copyInto(it, first.size)
        third.copyInto(it, first.size + second.size)
        it[firstThreeSize] = separator
        fourth.copyInto(it, withSeparatorSize)
    }
}

internal fun concatArrays(
    first: ByteArray,
    second: ByteArray,
    separator: Byte,
    third: ByteArray
): ByteArray {
    val firstTwoSize = first.size.checkedSizePlus(second.size)
    val withSeparatorSize = firstTwoSize.checkedSizePlus(1)
    val totalSize = withSeparatorSize.checkedSizePlus(third.size)
    return ByteArray(totalSize).also {
        first.copyInto(it, 0)
        second.copyInto(it, first.size)
        it[firstTwoSize] = separator
        third.copyInto(it, withSeparatorSize)
    }
}

internal fun packKey(first: ByteArray, second: ByteArray): ByteArray =
    concatArrays(first, second)

internal fun packKey(first: ByteArray, second: ByteArray, third: ByteArray): ByteArray =
    concatArrays(first, second, third)

internal fun packKey(first: ByteArray, second: ByteArray, third: ByteArray, fourth: ByteArray): ByteArray =
    concatArrays(first, second, third, fourth)

internal fun packKey(vararg segments: ByteArray): ByteArray =
    combineToByteArray(*segments)

internal fun packDescendingExclusiveEnd(includeStart: Boolean, vararg segments: ByteArray): ByteArray {
    val toCombine = if (includeStart) {
        Array(segments.size + 1) { index ->
            when {
                index < segments.size -> segments[index]
                else -> byteArrayOf(0)
            }
        }
    } else {
        segments
    }
    return combineToByteArray(*toCombine)
}

private fun Int.checkedSizePlus(addend: Int): Int {
    require(addend >= 0) { "Byte array size cannot be negative: $addend" }
    require(this <= Int.MAX_VALUE - addend) { "Combined byte array size exceeds Int range" }
    return this + addend
}
