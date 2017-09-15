package maryk.core.extensions.compare

import maryk.core.extensions.bytes.MAXBYTE
import kotlin.experimental.and

fun ByteArray.compareTo(other: ByteArray): Int  {
    (0 until minOf(this.size, other.size)).forEach {
        val a = this[it] and MAXBYTE
        val b = other[it] and MAXBYTE
        if (a != b) { return a - b }
    }
    return this.size - other.size
}
