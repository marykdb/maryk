package maryk.core.properties.types

import maryk.core.base64.Base64Maryk
import maryk.lib.extensions.compare.compareTo
import kotlin.io.encoding.ExperimentalEncodingApi

/** Value Data Objects which can be used to represent as fixed length bytes */
open class ValueDataObject(internal val _bytes: ByteArray) : Comparable<ValueDataObject> {
    override infix fun compareTo(other: ValueDataObject) = _bytes compareTo other._bytes

    @OptIn(ExperimentalEncodingApi::class)
    fun toBase64(): String = Base64Maryk.encode(this._bytes).trimEnd('=')

    fun toByteArray() = this._bytes

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
