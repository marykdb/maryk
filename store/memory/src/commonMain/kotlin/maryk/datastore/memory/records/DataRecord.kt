@file:Suppress("EXPERIMENTAL_API_USAGE")

package maryk.datastore.memory.records

import maryk.core.extensions.bytes.writeBytes
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ListReference
import maryk.core.properties.types.Key
import maryk.datastore.memory.records.DeleteState.NeverDeleted
import maryk.lib.extensions.compare.compareTo

internal data class DataRecord<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
    val key: Key<DM>,
    val values: List<DataRecordNode>,
    val firstVersion: ULong,
    var lastVersion: ULong,
    var isDeleted: DeleteState = NeverDeleted
) {
    /** Get value by [reference] */
    operator fun <T : Any> get(reference: IsPropertyReference<T, *, *>): T? =
        get(convertReferenceToByteArray(reference))

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

    /**
     * Set [value] at [reference] below [version]
     * Use [keepAllVersions] on true to keep all previous values
     * Add [validate] handler to pass previous value for validation
     */
    fun <T: Any> setValue(
        reference: IsPropertyReference<T, *, *>,
        value: T,
        version: ULong,
        keepAllVersions: Boolean = false,
        validate: ((T?) -> Unit)? = null
    ) {
        val referenceToCompareTo = convertReferenceToByteArray(reference)

        val valueIndex = values.binarySearch {
            it.reference.compareTo(referenceToCompareTo)
        }

        validate?.invoke(getValueAtIndex<T>(valueIndex)?.value)

        setValueAtIndex(valueIndex, referenceToCompareTo, value, version, keepAllVersions)
    }

    /**
     * Delete value by [reference] and record deletion below [version]
     * Add [validate] handler to pass previous value for validation
     */
    fun <T: Any> deleteByReference(
        reference: IsPropertyReference<T, IsPropertyDefinition<T>, *>,
        version: ULong,
        validate: ((T?) -> Unit)? = null
    ): DataRecordNode? {
        val referenceToCompareTo = convertReferenceToByteArray(reference)
        val valueIndex = values.binarySearch {
            it.reference.compareTo(referenceToCompareTo)
        }

        validate?.invoke(getValueAtIndex<T>(valueIndex)?.value)

        return deleteByIndex<T>(valueIndex, referenceToCompareTo, version)
    }

    /**
     * Get list at [reference] by reading and collecting all values from DataRecord
     */
    fun <T: Any> getList(
        reference: IsPropertyReference<out List<T>, IsPropertyDefinition<out List<T>>, out Any>
    ): MutableList<T> {
        val referenceToCompareTo = convertReferenceToByteArray(reference)

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

    /**
     * Set a list value for [reference] with a [newList] at new [version].
     * With [originalCount] it is determined if items need to be deleted.
     * Use [keepAllVersions] on true to keep old versions
     */
    fun <T: Any> setListValue(
        reference: IsPropertyReference<out List<T>, IsPropertyDefinition<out List<T>>, out Any>,
        newList: List<T>,
        originalCount: Int,
        version: ULong,
        keepAllVersions: Boolean
    ) {
        @Suppress("UNCHECKED_CAST")
        val listReference = reference as ListReference<T, *>
        val referenceToCompareTo = convertReferenceToByteArray(listReference)

        var valueIndex = values.binarySearch {
            it.reference.compareTo(referenceToCompareTo)
        }

        // Set the count
        this.setValueAtIndex(valueIndex, referenceToCompareTo, newList.size, version, keepAllVersions)

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

            this.setValueAtIndex(valueIndex, newRef, item, version, keepAllVersions)
        }
    }

    /** Get a value at [valueIndex] */
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

    /** Convert property [reference] to a ByteArray */
    private fun <T : Any> convertReferenceToByteArray(reference: IsPropertyReference<T, *, *>): ByteArray {
        var index = 0
        val referenceToCompareTo = ByteArray(reference.calculateStorageByteLength())
        reference.writeStorageBytes { referenceToCompareTo[index++] = it }
        return referenceToCompareTo
    }

    /** Delete value at [valueIndex] for [reference] and record it as [version] */
    private fun <T : Any> deleteByIndex(
        valueIndex: Int,
        reference: ByteArray,
        version: ULong
    ) =
        if (valueIndex < 0) {
            null
        } else {
            if (version > this.lastVersion) {
                this.lastVersion = version
            }
            when (val matchedValue = values[valueIndex]) {
                is DataRecordValue<*> -> {
                    DeletedValue<T>(reference, version).also {
                        (values as MutableList<DataRecordNode>)[valueIndex] = it
                    }
                }
                is DataRecordHistoricValues<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    DeletedValue<T>(reference, version).also {
                        (matchedValue.history as MutableList<IsDataRecordValue<*>>).add(it)
                    }
                }
                is DeletedValue<*> -> matchedValue
            }
        }

    /**
     * Sets a [value] at a specific [valueIndex] and stores it below [reference] at [version]
     * Use [keepAllVersions] on true to keep all versions
     */
    private fun setValueAtIndex(
        valueIndex: Int,
        reference: ByteArray,
        value: Any,
        version: ULong,
        keepAllVersions: Boolean
    ) {
        if (version > this.lastVersion) {
            this.lastVersion = version
        }

        if (valueIndex < 0) {
            // When not found add it
            (values as MutableList<DataRecordNode>).add(
                valueIndex * -1 - 1,
                DataRecordValue(reference, value, version)
            )
        } else when (val matchedValue = values[valueIndex]) {
            is DataRecordValue<*> -> {
                if (keepAllVersions) {
                    // Only store value if was not already value
                    if (matchedValue.value != value) {
                        @Suppress("UNCHECKED_CAST")
                        (values as MutableList<DataRecordNode>)[valueIndex] =
                                DataRecordHistoricValues(
                                    reference,
                                    listOf(
                                        matchedValue as DataRecordValue<Any>,
                                        DataRecordValue(reference, value, version)
                                    )
                                )
                    }
                } else {
                    val lastValue = (values as MutableList<DataRecordNode>).last()
                    // Only store value if was not already value
                    if (lastValue !is DataRecordValue<*> || lastValue.value != value) {
                        values[valueIndex] = DataRecordValue(reference, value, version)
                    }
                }
            }
            is DeletedValue<*> -> {
                (values as MutableList<DataRecordNode>)[valueIndex] =
                        DataRecordValue(reference, value, version)
            }
            is DataRecordHistoricValues<*> -> {
                val lastValue = (values as MutableList<DataRecordNode>).last()
                // Only store value if was not already value
                if (lastValue !is DataRecordValue<*> || lastValue.value != value) {
                    @Suppress("UNCHECKED_CAST")
                    (matchedValue.history as MutableList<DataRecordValue<*>>).add(
                        DataRecordValue(reference, value, version)
                    )
                }
            }
        }
    }
}

/** Match given [startBytes] against starting bytes of this byte array. Return true if match */
private fun ByteArray.matchStart(startBytes: ByteArray): Boolean {
    if (startBytes.size > this.size) return false

    startBytes.forEachIndexed { index, byte ->
        if (this[index] != byte) return false
    }
    return true
}
