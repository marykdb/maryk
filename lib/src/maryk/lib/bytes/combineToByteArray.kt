package maryk.lib.bytes

fun combineToByteArray(vararg elements: Any): ByteArray {
    var totalSize = 0
    for (element in elements) {
        totalSize = totalSize.checkedByteArraySizePlus(
            when (element) {
                is ByteArray -> element.size
                is Byte -> 1
                else -> throw IllegalArgumentException("Unsupported type: ${element::class.simpleName}")
            }
        )
    }

    val result = ByteArray(totalSize)
    var offset = 0

    for (element in elements) {
        when (element) {
            is ByteArray -> {
                element.copyInto(result, offset)
                offset += element.size
            }
            is Byte -> {
                result[offset] = element
                offset++
            }
        }
    }

    return result
}

internal fun Int.checkedByteArraySizePlus(addend: Int): Int {
    require(addend >= 0) { "Byte array size cannot be negative: $addend" }
    require(this <= Int.MAX_VALUE - addend) { "Combined byte array size exceeds Int range" }
    return this + addend
}
