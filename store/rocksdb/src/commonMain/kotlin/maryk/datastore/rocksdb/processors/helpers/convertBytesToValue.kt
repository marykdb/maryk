package maryk.datastore.rocksdb.processors.helpers

import maryk.core.properties.references.IsPropertyReference

/** Convert a byte array from [offset] until [length] with [reference] to a value of [T] */
fun <T : Any> ByteArray.convertToValue(
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
