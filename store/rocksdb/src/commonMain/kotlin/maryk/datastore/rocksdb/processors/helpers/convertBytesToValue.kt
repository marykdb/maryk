package maryk.datastore.rocksdb.processors.helpers

import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.SimpleTypedValueReference
import maryk.core.properties.types.TypedValue
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

    if (reference is SimpleTypedValueReference<*, *, *>) {
        val typedValue = readValue(reference.parentReference!!.propertyDefinition, reader) {
            length - readIndex + offset
        } as TypedValue<*, *>

        @Suppress("UNCHECKED_CAST")
        if (typedValue.type != reference.type) {
            return Unit as T
        } else {
            return typedValue.value as T
        }
    }

    @Suppress("UNCHECKED_CAST")
    return readValue(reference.comparablePropertyDefinition, reader) {
        length - readIndex + offset
    } as T
}
