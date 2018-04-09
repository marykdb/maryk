package maryk.core.properties.types

import maryk.lib.bytes.Base64
import maryk.lib.extensions.compare.compareTo

/** Value Data Objects which can be used to represent as fixed length bytes */
open class ValueDataObject(internal val _bytes: ByteArray) : Comparable<ValueDataObject> {
    override fun compareTo(other: ValueDataObject) = _bytes.compareTo(other._bytes)

    internal fun toBase64(): String = Base64.encode(this._bytes)

    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ValueDataObject) return false

        if (!_bytes.contentEquals(other._bytes)) return false

        return true
    }

    final override fun hashCode(): Int {
        return _bytes.hashCode()
    }
}
