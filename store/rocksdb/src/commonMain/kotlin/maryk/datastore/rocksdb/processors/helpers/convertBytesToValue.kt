package maryk.datastore.rocksdb.processors.helpers

import maryk.core.properties.references.IsPropertyReference
import maryk.datastore.shared.readValue

/** Convert a byte array from [offset] until [length] with [reference] to a value of [T] */
internal fun <T : Any> ByteArray.convertToValue(
    reference: IsPropertyReference<T, *, *>,
    offset: Int = 0,
    length: Int = this.size - offset
): T {
    var readIndex = offset
    val reader = {
        this[readIndex++]
    }

    @Suppress("UNCHECKED_CAST")
    return readValue(reference.comparablePropertyDefinition, reader) {
        length - readIndex + offset
    } as T
}
