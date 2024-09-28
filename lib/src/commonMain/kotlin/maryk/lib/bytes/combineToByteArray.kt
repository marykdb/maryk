package maryk.lib.bytes

fun combineToByteArray(vararg elements: Any): ByteArray {
    val totalSize = elements.sumOf { element ->
        when (element) {
            is ByteArray -> element.size
            is Byte -> 1
            else -> throw IllegalArgumentException("Unsupported type: ${element::class.simpleName}")
        }
    }

    val result = ByteArray(totalSize)
    var offset = 0

    elements.forEach { element ->
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
