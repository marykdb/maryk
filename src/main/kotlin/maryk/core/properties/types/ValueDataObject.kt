package maryk.core.properties.types

import maryk.core.bytes.Base64
import maryk.core.extensions.compare.compareTo
import java.util.*

/** Value Data Objects which can be used to represent as fixed length bytes */
open class ValueDataObject(internal val _bytes: ByteArray) : Comparable<ValueDataObject> {
    override fun compareTo(other: ValueDataObject) = _bytes.compareTo(other._bytes)

    fun toBase64(): String = Base64.encode(this._bytes)

    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ValueDataObject) return false

        if (!Arrays.equals(_bytes, other._bytes)) return false

        return true
    }

    final override fun hashCode(): Int {
        return Arrays.hashCode(_bytes)
    }
}
