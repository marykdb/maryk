@file:Suppress("EXPERIMENTAL_API_USAGE")

package maryk.core.processors.datastore.memory.records

import maryk.core.models.IsRootValuesDataModel
import maryk.core.processors.datastore.memory.records.DeleteState.NeverDeleted
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Key

internal data class DataRecord<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
    val key: Key<DM>,
    val values: List<IsDataRecordValue>,
    val firstVersion: ULong,
    val lastVersion: ULong,
    val isDeleted: DeleteState = NeverDeleted
) {
    /** Get value by [reference] */
    operator fun <T: Any> get(reference: IsPropertyReference<T, *, *>): T {
        var index = 0
        val referenceToCompareTo = ByteArray(reference.calculateStorageByteLength())
        reference.writeStorageBytes { referenceToCompareTo[index++] = it }

        val matchedValue = values.find { referenceToCompareTo.contentEquals(it.reference) }

        return if (matchedValue is DataRecordValue<*>) {
            @Suppress("UNCHECKED_CAST")
            matchedValue.value as T
        } else throw Exception("Unexpected Value $matchedValue from $reference")
    }
}
