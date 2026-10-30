package maryk.core.processors.datastore

import maryk.core.exceptions.RequestException
import maryk.core.properties.definitions.IncrementingMapDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.types.numeric.NumberType
import maryk.lib.extensions.compare.prevByteInSameLength

/** [addValues] to an incrementing map defined by [definition] with [currentIncMapKey] to storage with [valueWriter]. */
@Suppress("UNCHECKED_CAST")
fun <K: Comparable<K>, V : Any, D : IncrementingMapDefinition<K, V, *>> writeIncMapAdditionsToStorage(
    currentIncMapKey: ByteArray,
    valueWriter: ValueWriter<D>,
    definition: D,
    addValues: List<V>
): List<K> {
    if (addValues.isNotEmpty() && definition.keyNumberDescriptor.type !in listOf(
            NumberType.SInt8Type, NumberType.SInt16Type, NumberType.SInt32Type, NumberType.SInt64Type,
            NumberType.UInt8Type, NumberType.UInt16Type, NumberType.UInt32Type, NumberType.UInt64Type,
        )) {
        throw RequestException("Automatic incrementing-map allocation requires an integer key descriptor")
    }
    val qualifierLength = currentIncMapKey.size
    var nextIncMapKey = currentIncMapKey
    val qualifierWriter: QualifierWriter = { writer ->
        nextIncMapKey.forEach(writer)
    }

    val keyDefinition = definition.keyDefinition

    return addValues.map { mapValue ->
        nextIncMapKey = nextIncMapKey.prevByteInSameLength(keyDefinition.byteSize)
        writeValue(
            null, qualifierLength, qualifierWriter,
            definition.valueDefinition,
            mapValue,
            valueWriter as ValueWriter<IsSubDefinition<*, *>>
        )

        var keyIndex = currentIncMapKey.size - keyDefinition.byteSize
        keyDefinition.readStorageBytes(keyDefinition.byteSize) {
            nextIncMapKey[keyIndex++]
        }
    }
}
