package maryk.core.processors.datastore

import maryk.core.properties.definitions.IncrementingMapDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.lib.extensions.compare.prevByteInSameLength

/** [addValues] to an incrementing map defined by [definition] with [currentIncMapKey] to storage with [valueWriter]. */
@Suppress("UNCHECKED_CAST")
fun <K: Comparable<K>, V : Any, D : IncrementingMapDefinition<K, V, *>> writeIncMapAdditionsToStorage(
    currentIncMapKey: ByteArray,
    valueWriter: ValueWriter<D>,
    definition: D,
    addValues: List<V>
): MutableList<K> {
    val qualifierLength = currentIncMapKey.size
    var nextIncMapKey = currentIncMapKey
    val qualifierWriter: QualifierWriter = { writer ->
        for (writeIndex in 0..nextIncMapKey.lastIndex) {
            writer(nextIncMapKey[writeIndex])
        }
    }

    val addedKeys = mutableListOf<K>()

    // Process Map Values
    val mapDefinition = (definition as IsMapDefinition<in Any, *, *>)
    for (mapValue in addValues) {
        // Inc Map keys are stored in reverse order so latest is always first.
        // So lower the latest value to get at the next value
        nextIncMapKey = nextIncMapKey.prevByteInSameLength(definition.keyDefinition.byteSize)
        writeValue(
            null, qualifierLength, qualifierWriter,
            mapDefinition.valueDefinition,
            mapValue,
            valueWriter as ValueWriter<IsSubDefinition<*, *>>
        )

        // Add key to list to send back to requester
        var keyIndex = currentIncMapKey.size - definition.keyDefinition.byteSize
        addedKeys.add(
            definition.keyDefinition.readStorageBytes(definition.keyDefinition.byteSize) {
                nextIncMapKey[keyIndex++]
            }
        )
    }

    return addedKeys
}
