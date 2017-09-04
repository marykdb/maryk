package maryk.core.properties.types

import maryk.core.bytes.Base64
import maryk.core.extensions.compare.compareTo

/** Value Data Objects which can be used to represent as fixed length bytes */
open class ValueDataObject(internal val _bytes: ByteArray) : Comparable<ValueDataObject> {
    override fun compareTo(other: ValueDataObject) = _bytes.compareTo(other._bytes)

    fun toBase64(): String = Base64.encode(this._bytes)
}
