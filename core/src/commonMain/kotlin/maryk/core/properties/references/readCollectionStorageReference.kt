package maryk.core.properties.references

import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.initUInt
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.definitions.IsStorageBytesEncodable
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.lib.exceptions.ParseException

internal fun IsPropertyDefinition<*>.anonymousCollectionReferenceType(): ReferenceType? = when (this) {
    is IsMapDefinition<*, *, *> -> ReferenceType.MAP
    is IsListDefinition<*, *> -> ReferenceType.LIST
    is IsSetDefinition<*, *> -> ReferenceType.SET
    else -> null
}

/** Nested collection values have the established anonymous index-zero collection header. */
@Suppress("UNCHECKED_CAST")
internal fun CanHaveComplexChildReference<*, *, *, *>.readCollectionStorageReference(
    reader: () -> Byte,
    isDoneReading: () -> Boolean,
): AnyPropertyReference? {
    val expectedType = propertyDefinition.anonymousCollectionReferenceType() ?: return null
    decodeStorageIndex(reader) { index, type ->
        if (index != 0u || type != expectedType) throw ParseException("Expected anonymous $expectedType storage reference")
    }
    if (isDoneReading()) return this
    return when (val definition = propertyDefinition) {
    is IsMapDefinition<*, *, *> -> {
        val map = definition as IsMapDefinition<Any, Any, IsPropertyContext>
        val keyLength = readStorageComponentLength(reader, isDoneReading)
        MapValueReference(map.keyDefinition.readStorageBytes(keyLength, reader), map, this as CanContainMapItemReference<*, *, *>)
    }
    is IsListDefinition<*, *> -> ListItemReference(
        initUInt(reader), definition as IsListDefinition<Any, IsPropertyContext>, this as CanContainListItemReference<*, *, *>,
    )
    is IsSetDefinition<*, *> -> {
        val set = definition as IsSetDefinition<Any, IsPropertyContext>
        val itemLength = readStorageComponentLength(reader, isDoneReading)
        val value = (set.valueDefinition as IsStorageBytesEncodable<Any>).readStorageBytes(itemLength, reader)
        SetItemReference(value, set, this as CanContainSetItemReference<*, *, *>)
    }
    else -> null
}
}

private fun readStorageComponentLength(reader: () -> Byte, isDoneReading: () -> Boolean): Int {
    val length = initIntByVar {
        if (isDoneReading()) throw ParseException("Missing collection component length in storage reference")
        reader()
    }
    if (length < 0) throw ParseException("Negative collection component length in storage reference")
    return length
}
