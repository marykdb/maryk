package maryk.core.properties.references

import maryk.lib.exceptions.ParseException
import kotlin.experimental.and

/* Reference type to be encoded in last 3 bits of byte */
enum class ReferenceType(val value: Byte) {
    SPECIAL(0),
    VALUE(1),
    LIST(2),
    SET(3),
    MAP(4),
    TYPE(5),
    EMBED(6),
    // Only add items that are used regularly in storage to
}

/* Reference type to be encoded in last 3 bits of byte */
enum class CompleteReferenceType(val value: Byte) {
    DELETE(0),
    VALUE(1),
    LIST(2),
    SET(3),
    MAP(4),
    TYPE(5),
    EMBED(6),

    // These fall outside the space and are encoded with SPECIAL (Last 3 bits 0)
    MAP_KEY(0b1000)
    // Binary counting so next is 0b10000
}

/** Retrieve reference storage type from the [byte] */
internal fun referenceStorageTypeOf(byte: Byte): ReferenceType {
    val byteToCompare = byte and 0b111
    return ReferenceType.values().firstOrNull { it.value == byteToCompare }
        ?: throw ParseException("Unknown ReferenceType $byteToCompare")
}

/** Retrieve reference storage type from the [byte] */
internal fun completeReferenceTypeOf(byte: Byte) =
    CompleteReferenceType.values().firstOrNull { it.value == byte }
        ?: throw ParseException("Unknown CompleteReferenceType $byte")
