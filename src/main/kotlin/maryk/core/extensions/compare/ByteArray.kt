package maryk.core.extensions.compare

import maryk.core.extensions.bytes.MAX_BYTE
import kotlin.experimental.and

fun ByteArray.compareTo(other: ByteArray): Int  {
    (0 until minOf(this.size, other.size)).forEach {
        val a = this[it] and MAX_BYTE
        val b = other[it] and MAX_BYTE
        if (a != b) { return a - b }
    }
    return this.size - other.size
}
