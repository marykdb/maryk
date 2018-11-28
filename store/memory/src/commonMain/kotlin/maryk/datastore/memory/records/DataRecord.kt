@file:Suppress("EXPERIMENTAL_API_USAGE")

package maryk.datastore.memory.records

import maryk.core.extensions.bytes.writeBytes
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ListReference
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
    operator fun <T : Any> get(reference: IsPropertyReference<T, *, *>): T? =
        get(getReferenceAsByteArray(reference))

    /** Get value by [reference] */
    operator fun <T : Any> get(reference: ByteArray): T? =
        getValue<T>(reference)?.value

    /** Get value by [reference] */
    fun <T : Any> getValue(reference: ByteArray): DataRecordValue<T>? {
        val valueIndex = values.binarySearch {
            it.reference.compareTo(reference)
        }
        return getValueAtIndex(valueIndex)
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

        setValueAtIndex(valueIndex, referenceToCompareTo, value, version, isWithHistory)
    }

    fun <T: Any> deleteByReference(
        reference: AnyPropertyReference,
        version: ULong
    ): IsDataRecordNode? {
        val referenceToCompareTo = getReferenceAsByteArray(reference)
        val valueIndex = values.binarySearch {
            it.reference.compareTo(referenceToCompareTo)
        }
        return deleteByIndex<T>(valueIndex, referenceToCompareTo, version)
    }

    fun <T: Any> getList(reference: IsPropertyReference<out List<T>, IsPropertyDefinition<out List<T>>, out Any>): MutableList<T> {
        val referenceToCompareTo = getReferenceAsByteArray(reference)

        var valueIndex = values.binarySearch {
            it.reference.compareTo(referenceToCompareTo)
        }

        val count = getValueAtIndex<Int>(valueIndex)?.value ?: 0
        valueIndex++ // skip count index

        val list = ArrayList<T>(count)
        while (valueIndex < values.size) {
            val node = getValueAtIndex<T>(valueIndex) ?: continue

            // Break if reference does not match list start
            if (!node.reference.matchStart(referenceToCompareTo)) {
                break
            }

            list.add(node.value)
            valueIndex++
        }
        return list
    }

    fun <T: Any> setListValue(
        reference: IsPropertyReference<out List<T>, IsPropertyDefinition<out List<T>>, out Any>,
        newList: List<T>,
        originalCount: Int,
        version: ULong,
        isWithHistory: Boolean
    ) {
        @Suppress("UNCHECKED_CAST")
        val listReference = reference as ListReference<T, *>
        val referenceToCompareTo = getReferenceAsByteArray(listReference)

        var valueIndex = values.binarySearch {
            it.reference.compareTo(referenceToCompareTo)
        }

        // Set the count
        this.setValueAtIndex(valueIndex, referenceToCompareTo, newList.size, version, isWithHistory)

        val toDeleteCount = originalCount - newList.size
        if (toDeleteCount > 0) {
            for(i in 0..toDeleteCount) {
                deleteByIndex<T>(valueIndex + i, getValueAtIndex<T>(valueIndex+i)!!.reference, version)
            }
        }

        // Where is last addition
        val lastAdditionIndex = if (valueIndex > 0 && toDeleteCount < 0) {
            valueIndex + originalCount
        } else 0

        // Walk all new values to store
        newList.forEachIndexed { index, item ->
            var byteIndex = referenceToCompareTo.size
            val newRef = referenceToCompareTo.copyOf(byteIndex + 4)

            index.toUInt().writeBytes({
                newRef[byteIndex++] = it
            })

            if (valueIndex < 0) {
                valueIndex--
            } else {
                if(lastAdditionIndex <= valueIndex) {
                    valueIndex = valueIndex * -1 - 2
                } else valueIndex++
            }

            this.setValueAtIndex(valueIndex, newRef, item, version, isWithHistory)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T: Any> getValueAtIndex(valueIndex: Int): DataRecordValue<T>? {
        return if (valueIndex < 0) {
            null
        } else  when (val node = values[valueIndex]) {
            is DataRecordValue<*> -> node as DataRecordValue<T>
            is DataRecordHistoricValues<*> -> when (val lastValue = node.history.last()) {
                is DataRecordValue<*> -> lastValue as DataRecordValue<T>
                else -> null // deletion
            }
            else -> null
        }
    }

    private fun <T : Any> getReferenceAsByteArray(reference: IsPropertyReference<T, *, *>): ByteArray {
        var index = 0
        val referenceToCompareTo = ByteArray(reference.calculateStorageByteLength())
        reference.writeStorageBytes { referenceToCompareTo[index++] = it }
        return referenceToCompareTo
    }

    private fun <T : Any> deleteByIndex(
        valueIndex: Int,
        referenceToCompareTo: ByteArray,
        version: ULong
    ) =
        if (valueIndex < 0) {
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

    private fun setValueAtIndex(
        valueIndex: Int,
        referenceToCompareTo: ByteArray,
        value: Any,
        version: ULong,
        isWithHistory: Boolean
    ) {
        if (valueIndex < 0) {
            // When not found add it
            (values as MutableList<IsDataRecordNode>).add(
                valueIndex * -1 - 1,
                DataRecordValue(referenceToCompareTo, value, version)
            )
        } else when (val matchedValue = values[valueIndex]) {
            is DataRecordValue<*> -> {
                if (isWithHistory) {
                    // Only store value if was not already value
                    if (matchedValue.value != value) {
                        @Suppress("UNCHECKED_CAST")
                        (values as MutableList<IsDataRecordNode>)[valueIndex] =
                                DataRecordHistoricValues(
                                    referenceToCompareTo,
                                    listOf(
                                        matchedValue as DataRecordValue<Any>,
                                        DataRecordValue(referenceToCompareTo, value, version)
                                    )
                                )
                    }
                } else {
                    val lastValue = (values as MutableList<IsDataRecordNode>).last()
                    // Only store value if was not already value
                    if (lastValue !is DataRecordValue<*> || lastValue.value != value) {
                        values[valueIndex] = DataRecordValue(referenceToCompareTo, value, version)
                    }
                }
            }
            is DeletedValue<*> -> {
                (values as MutableList<IsDataRecordNode>)[valueIndex] =
                        DataRecordValue(referenceToCompareTo, value, version)
            }
            is DataRecordHistoricValues<*> -> {
                val lastValue = (values as MutableList<IsDataRecordNode>).last()
                // Only store value if was not already value
                if (lastValue !is DataRecordValue<*> || lastValue.value != value) {
                    @Suppress("UNCHECKED_CAST")
                    (matchedValue.history as MutableList<DataRecordValue<*>>).add(
                        DataRecordValue(referenceToCompareTo, value, version)
                    )
                }
            }
        }
    }
}

private fun ByteArray.matchStart(startBytes: ByteArray): Boolean {
    if (startBytes.size > this.size) return false

    startBytes.forEachIndexed { index, byte ->
        if (this[index] != byte) return false
    }
    return true
}
