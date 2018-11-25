@file:Suppress("EXPERIMENTAL_API_USAGE")

package maryk.datastore.memory.records

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Key
import maryk.datastore.memory.records.DeleteState.NeverDeleted
import maryk.lib.extensions.compare.compareTo

internal data class DataRecord<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
    val key: Key<DM>,
    val values: List<IsDataRecordNode>,
    val firstVersion: ULong,
    val lastVersion: ULong,
    val isDeleted: DeleteState = NeverDeleted
) {
    /** Get value by [reference] */
    operator fun <T : Any> get(reference: IsPropertyReference<T, *, *>): T? {
        val referenceToCompareTo = getReferenceAsByteArray(reference)

        val valueIndex = values.binarySearch {
            it.reference.compareTo(referenceToCompareTo)
        }

        @Suppress("UNCHECKED_CAST")
        return if (valueIndex < 0) {
            null
        } else when (val matchedValue = values[valueIndex]) {
            is DataRecordValue<*> -> matchedValue.value as T
            is DataRecordHistoricValues<*> -> when (val lastValue = matchedValue.history.last()){
                is DataRecordValue<*> -> lastValue.value as T
                else -> null // deletion
            }
            else -> null
        }
    }

    fun <T: Any> setValue(
        reference: IsPropertyReference<T, *, *>,
        value: T,
        version: ULong,
        isWithHistory: Boolean = false
    ) {
        val referenceToCompareTo = getReferenceAsByteArray(reference)

        val valueIndex = values.binarySearch {
            it.reference.compareTo(referenceToCompareTo)
        }

        if (valueIndex < 0) {
            // When not found add it
            (values as MutableList<IsDataRecordNode>).add(
                valueIndex * -1 - 1,
                DataRecordValue(referenceToCompareTo, value, version)
            )
        } else when (val matchedValue = values[valueIndex]) {
            is DataRecordValue<*> -> {
                if (isWithHistory) {
                    @Suppress("UNCHECKED_CAST")
                    (values as MutableList<IsDataRecordNode>)[valueIndex] =
                            DataRecordHistoricValues(
                                referenceToCompareTo,
                                listOf(
                                    matchedValue as DataRecordValue<T>,
                                    DataRecordValue(referenceToCompareTo, value, version)
                                )
                            )

                } else {
                    (values as MutableList<IsDataRecordNode>)[valueIndex] =
                            DataRecordValue(referenceToCompareTo, value, version)
                }
            }
            is DeletedValue<*> -> {
                (values as MutableList<IsDataRecordNode>)[valueIndex] =
                        DataRecordValue(referenceToCompareTo, value, version)
            }
            is DataRecordHistoricValues<*> -> {
                @Suppress("UNCHECKED_CAST")
                (matchedValue.history as MutableList<DataRecordValue<*>>).add(
                    DataRecordValue(referenceToCompareTo, value, version)
                )
            }
        }
    }

    fun <T: Any>deleteByReference(
        reference: AnyPropertyReference,
        version: ULong
    ): IsDataRecordNode? {
        val referenceToCompareTo = getReferenceAsByteArray(reference)

        val valueIndex = values.binarySearch {
            it.reference.compareTo(referenceToCompareTo)
        }

        return if (valueIndex < 0) {
            null
        } else {
            when (val matchedValue = values[valueIndex]) {
                is DataRecordValue<*> -> {
                    DeletedValue<T>(referenceToCompareTo, version).also {
                        (values as MutableList<IsDataRecordNode>)[valueIndex] = it
                    }
                }
                is DataRecordHistoricValues<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    DeletedValue<T>(referenceToCompareTo, version).also {
                        (matchedValue.history as MutableList<IsDataRecordValue<*>>).add(it)
                    }
                }
                is DeletedValue<*> -> matchedValue
            }
        }
    }

    private fun <T : Any> getReferenceAsByteArray(reference: IsPropertyReference<T, *, *>): ByteArray {
        var index = 0
        val referenceToCompareTo = ByteArray(reference.calculateStorageByteLength())
        reference.writeStorageBytes { referenceToCompareTo[index++] = it }
        return referenceToCompareTo
    }
}
