package maryk.datastore.hbase.helpers

import maryk.core.exceptions.StorageException
import maryk.core.models.values
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.definitions.IsStorageBytesEncodable
import maryk.core.properties.definitions.wrapper.IsMapDefinitionWrapper
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.references.EmbeddedValuesPropertyRef
import maryk.core.properties.references.IsMapReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ListItemReference
import maryk.core.properties.references.ListReference
import maryk.core.properties.references.MapReference
import maryk.core.properties.references.MapValueReference
import maryk.core.properties.references.MultiTypePropertyReference
import maryk.core.properties.references.SetItemReference
import maryk.core.properties.references.SetReference
import maryk.core.properties.references.TypedPropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.values.EmptyValueItems
import maryk.datastore.hbase.dataColumnFamily
import maryk.datastore.shared.TypeIndicator
import maryk.lib.extensions.compare.compareToWithOffsetLength
import maryk.lib.extensions.compare.prevByteInSameLength
import org.apache.hadoop.hbase.client.Put
import org.apache.hadoop.hbase.client.Result

internal fun <T : Any> deleteByReference(
    currentRowResult: Result,
    put: Put,
    reference: TypedPropertyReference<T>,
    referenceAsBytes: ByteArray,
    handlePreviousValue: ((ByteArray, T?) -> Unit)?
): Boolean {
    // Only set with lists where it should look at the parent
    var referenceOfParentOfList: ByteArray? = null
    var toShiftListCount = 0u

    var shouldHandlePrevValue = true

    // Value to delete
    @Suppress("UNCHECKED_CAST")
    val prevValue: T = currentRowResult.getColumnLatestCell(dataColumnFamily, referenceAsBytes)?.let { cell ->
        if (cell.valueLength == 1 && cell.qualifierArray[cell.qualifierOffset] == TypeIndicator.DeletedIndicator.byte) {
            // does not exist so nothing to delete
            null
        } else {
            // With delete the prev value for complex types needs to be set to check final and required states
            // Only current values are checked on content
            when (reference) {
                is MapReference<*, *, *> -> mapOf<Any, Any>() as T
                is ListReference<*, *> -> listOf<Any>() as T
                is SetReference<*, *> -> setOf<Any>() as T
                is EmbeddedValuesPropertyRef<*, *> -> reference.propertyDefinition.definition.dataModel.values { EmptyValueItems } as T
                is MapValueReference<*, *, *> -> {
                    val mapReference = reference.parentReference as IsMapReference<Any, Any, IsPropertyContext, IsMapDefinitionWrapper<Any, Any, Any, IsPropertyContext, *>>

                    createCountUpdater(currentRowResult, mapReference, put, -1) { count ->
                        mapReference.propertyDefinition.definition.validateSize(count) { mapReference }
                    }

                    // Map values can be set to null to be deleted.
                    shouldHandlePrevValue = false

                    when (val valueDefinition = mapReference.propertyDefinition.definition.valueDefinition) {
                        is IsStorageBytesEncodable<*> ->
                            valueDefinition.fromStorageBytes(cell.valueArray, cell.valueOffset, cell.valueLength) as T
                        is EmbeddedValuesDefinition<*> ->
                            valueDefinition.dataModel.values { EmptyValueItems } as T
                        is IsMapDefinition<*, *, *> -> mapOf<Any, Any>() as T
                        is IsListDefinition<*, *> -> listOf<Any>() as T
                        is IsSetDefinition<*, *> -> setOf<Any>() as T
                        is IsMultiTypeDefinition<*, *, *> -> {
                            cell.readValue(valueDefinition) as T
                        }
                        else -> throw StorageException("Unknown map type")
                    }
                }
                is ListItemReference<*, *> -> {
                    val listReference = reference.parentReference as ListReference<Any, IsPropertyContext>
                    val listDefinition = listReference.propertyDefinition.definition
                    createCountUpdater(
                        currentRowResult,
                        listReference as IsPropertyReference<List<*>, IsPropertyDefinition<List<*>>, out Any>,
                        put,
                        -1,
                    ) { newCount ->
                        toShiftListCount = newCount - reference.index
                        listDefinition.validateSize(newCount) { listReference }
                    }
                    referenceOfParentOfList = listReference.toStorageByteArray()
                    // Map values can be set to null to be deleted.
                    shouldHandlePrevValue = false
                    (listDefinition.valueDefinition as IsStorageBytesEncodable<T>).fromStorageBytes(cell.valueArray, cell.valueOffset, cell.valueLength)
                }
                is SetItemReference<*, *> -> {
                    val setReference = reference.parentReference as SetReference<Any, IsPropertyContext>

                    createCountUpdater(
                        currentRowResult,
                        setReference as IsPropertyReference<Set<*>, IsPropertyDefinition<Set<*>>, out Any>,
                        put,
                        -1,
                    ) { newCount ->
                        setReference.propertyDefinition.definition.validateSize(newCount) { setReference }
                    }
                    // Map values can be set to null to be deleted.
                    shouldHandlePrevValue = false
                    (setReference.propertyDefinition.valueDefinition as IsStorageBytesEncodable<T>).fromStorageBytes(cell.valueArray, cell.valueOffset, cell.valueLength)
                }
                is MultiTypePropertyReference<*, *, *, *, *> -> {
                    cell.readValue(reference.comparablePropertyDefinition).let {
                        when (it) {
                            is TypedValue<*, *> -> it
                            is MultiTypeEnum<*> -> TypedValue(it, Unit) as T
                            else -> throw StorageException("Unknown type for T")
                        }
                    } as T
                }
                else -> (reference as IsStorageBytesEncodable<T>).fromStorageBytes(cell.valueArray, cell.valueOffset, cell.valueLength)
            }
        }
    } ?: return false

    if (shouldHandlePrevValue) {
        // Primarily for validations
        handlePreviousValue?.invoke(referenceAsBytes, prevValue)
    }

    // Delete value and complex sub parts below same reference
    val refOfParentOfList = referenceOfParentOfList
    var shouldLook = false

    for (cell in currentRowResult.rawCells()) {
        if (!shouldLook) {
            shouldLook = referenceAsBytes.compareToWithOffsetLength(cell.qualifierArray, cell.qualifierOffset, cell.qualifierLength) <= 0
        }

        if (shouldLook) {
            // Exact match
            if (referenceAsBytes.compareToWithOffsetLength(cell.qualifierArray, cell.qualifierOffset, referenceAsBytes.size) == 0) {
                // Delete if not a list or no further list items
                if (toShiftListCount <= 0u) {
                    put.addColumn(dataColumnFamily, cell.qualifierArray.copyOfRange(cell.qualifierOffset, cell.qualifierOffset + cell.qualifierLength), TypeIndicator.DeletedIndicator.byteArray)
                }
            } else if (refOfParentOfList != null && refOfParentOfList.compareToWithOffsetLength(cell.qualifierArray, cell.qualifierOffset, refOfParentOfList.size) == 0) {
                // To handle list shifting
                if (toShiftListCount > 0u) {
                    val newShiftedQualifier = cell.qualifierArray.copyOfRange(cell.qualifierOffset, cell.qualifierOffset + cell.qualifierLength).prevByteInSameLength()
                    val newValue = cell.valueArray.copyOfRange(cell.valueOffset, cell.valueOffset + cell.valueLength)
                    put.addColumn(dataColumnFamily, newShiftedQualifier, newValue)
                    toShiftListCount--
                }

                if (toShiftListCount <= 0u) {
                    put.addColumn(dataColumnFamily, cell.qualifierArray.copyOfRange(cell.qualifierOffset, cell.qualifierOffset + cell.qualifierLength), TypeIndicator.DeletedIndicator.byteArray)
                }
            } else {
                // Non relevant qualifiers so break
                break
            }
        }
    }

    return true
}
