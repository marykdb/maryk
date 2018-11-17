package maryk.core.properties.references

import maryk.lib.exceptions.ParseException

/* Reference type to be encoded in last 3 bits of byte */
enum class ReferenceType(val value: Byte) {
    SPECIAL(0),
    VALUE(1),
    LIST(2),
    SET(3),
    MAP(4)
    // Only add items that are used regularly in storage to
}

/** Retrieve reference storage type from the [byte] */
internal fun referenceStorageTypeOf(byte: Byte) =
    ReferenceType.values().getOrNull(byte.toInt())
        ?: throw ParseException("Unknown ReferenceType $byte")

/* Special encoding which use the complete byte */
enum class ReferenceSpecialType(val value: Byte) {
    DELETE(0),
    TYPE(0b1000),
    MAP_KEY(0b10000),
    // Binary counting so next is 0b11000
}

/** Retrieve reference storage type from the [byte] */
internal fun referenceStorageSpecialTypeOf(byte: Byte) =
    ReferenceSpecialType.values().getOrNull(byte.toInt())
        ?: throw ParseException("Unknown ReferenceSpecialType $byte")
