package maryk.core.properties.references

import maryk.lib.exceptions.ParseException
import kotlin.experimental.and

/* Reference type to be encoded in last 3 bits of byte */
enum class ReferenceType(val value: Byte) {
    DELETE(0),
    VALUE(1),
    LIST(2),
    SET(3),
    MAP(4),
    TYPE(5),
    EMBED(6),
}

/** Retrieve reference storage type from the [byte] */
internal fun referenceStorageTypeOf(byte: Byte): ReferenceType {
    val byteToCompare = byte and 0b111
    return ReferenceType.entries.firstOrNull { it.value == byteToCompare }
        ?: throw ParseException("Unknown ReferenceType $byteToCompare")
}
