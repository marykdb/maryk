package maryk.datastore.rocksdb.processors.helpers

import maryk.core.exceptions.StorageException
import maryk.core.extensions.bytes.initUIntByVarWithExtraInfo
import maryk.core.models.emptyValues
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
import maryk.core.properties.references.IncMapReference
import maryk.core.properties.references.IsMapReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceWithParent
import maryk.core.properties.references.ListItemReference
import maryk.core.properties.references.ListReference
import maryk.core.properties.references.MapReference
import maryk.core.properties.references.MapValueReference
import maryk.core.properties.references.MultiTypePropertyReference
import maryk.core.properties.references.SetItemReference
import maryk.core.properties.references.SetReference
import maryk.core.properties.references.TypedPropertyReference
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.invoke
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.Transaction
import maryk.datastore.shared.TypeIndicator
import maryk.datastore.shared.readValue
import maryk.lib.extensions.compare.matchesRangePart
import maryk.lib.extensions.compare.prevByteInSameLength
import maryk.rocksdb.ReadOptions

internal fun <T : Any> deleteByReference(
    transaction: Transaction,
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    key: Key<*>,
    reference: TypedPropertyReference<T>,
    referenceAsBytes: ByteArray,
    version: ByteArray,
    handlePreviousValue: ((ByteArray, T?) -> Unit)?
): Boolean {
    val referenceToCompareTo = key.bytes + referenceAsBytes
    var referenceOfParent: ByteArray? = null
    var toShiftListCount = 0u

    var shouldHandlePrevValue = true

    // Value to delete
    @Suppress("UNCHECKED_CAST")
    val prevValue: T = transaction.getValue(columnFamilies, readOptions, null, referenceToCompareTo) { b, o, l ->
        if (l == 1 && b[o] == TypeIndicator.DeletedIndicator.byte) {
            // does not exist so nothing to delete
            null
        } else {
            // With delete the prev value for complex types needs to be set to check final and required states
            // Only current values are checked on content
            when (reference) {
                is MapReference<*, *, *> -> mapOf<Any, Any>() as T
                is ListReference<*, *> -> listOf<Any>() as T
                is SetReference<*, *> -> setOf<Any>() as T
                is EmbeddedValuesPropertyRef<*, *> -> reference.propertyDefinition.definition.dataModel.emptyValues() as T
                is MapValueReference<*, *, *> -> {
                    val mapReference = reference.parentReference as IsMapReference<Any, Any, IsPropertyContext, IsMapDefinitionWrapper<Any, Any, Any, IsPropertyContext, *>>
                    createCountUpdater(
                        transaction,
                        columnFamilies,
                        readOptions,
                        key,
                        mapReference as IsPropertyReference<Map<*, *>, IsPropertyDefinition<Map<*, *>>, out Any>,
                        version,
                        -1
                    ) { newCount ->
                        mapReference.propertyDefinition.definition.validateSize(newCount) { mapReference }
                    }
                    // Map values can be set to null to be deleted.
                    shouldHandlePrevValue = false

                    when (val valueDefinition = mapReference.propertyDefinition.definition.valueDefinition) {
                        is IsStorageBytesEncodable<*> ->
                            valueDefinition.fromStorageBytes(b, o, l) as T
                        is EmbeddedValuesDefinition<*> ->
                            valueDefinition.dataModel.emptyValues() as T
                        is IsMapDefinition<*, *, *> -> mapOf<Any, Any>() as T
                        is IsListDefinition<*, *> -> listOf<Any>() as T
                        is IsSetDefinition<*, *> -> setOf<Any>() as T
                        is IsMultiTypeDefinition<*, *, *> -> {
                            var readIndex = o
                            val reader = {
                                b[readIndex++]
                            }
                            readValue(valueDefinition, reader) {
                                l - readIndex
                            } as T
                        }
                        else -> throw StorageException("Unknown map type")
                    }
                }
                is ListItemReference<*, *> -> {
                    val listReference = reference.parentReference as ListReference<Any, IsPropertyContext>
                    val listDefinition = listReference.propertyDefinition.definition
                    createCountUpdater(
                        transaction,
                        columnFamilies,
                        readOptions,
                        key,
                        listReference as IsPropertyReference<List<*>, IsPropertyDefinition<List<*>>, out Any>,
                        version,
                        -1
                    ) { newCount ->
                        toShiftListCount = newCount - reference.index
                        listDefinition.validateSize(newCount) { listReference }
                    }
                    referenceOfParent = listReference.toStorageByteArray(key.bytes)
                    // Map values can be set to null to be deleted.
                    shouldHandlePrevValue = false
                    (listDefinition.valueDefinition as IsStorageBytesEncodable<T>).fromStorageBytes(b, o, l)
                }
                is SetItemReference<*, *> -> {
                    val setReference = reference.parentReference as SetReference<Any, IsPropertyContext>

                    var newO = o
                    // Missing read by var int for size of element
                    initUIntByVarWithExtraInfo({
                        b[newO++]
                    }) { _, _ -> 1 }
                    createCountUpdater(
                        transaction,
                        columnFamilies,
                        readOptions,
                        key,
                        setReference as IsPropertyReference<Set<*>, IsPropertyDefinition<Set<*>>, out Any>,
                        version,
                        -1
                    ) { newCount ->
                        setReference.propertyDefinition.definition.validateSize(newCount) { setReference }
                    }
                    // Map values can be set to null to be deleted.
                    shouldHandlePrevValue = false
                    (setReference.propertyDefinition.valueDefinition as IsStorageBytesEncodable<T>).fromStorageBytes(b, newO, l)
                }
                is MultiTypePropertyReference<*, *, *, *, *> -> {
                    var readIndex = o
                    val reader = {
                        b[readIndex++]
                    }
                    readValue(reference.comparablePropertyDefinition, reader) {
                        o + l - readIndex
                    }.let {
                        when (it) {
                            is TypedValue<*, *> -> it
                            is MultiTypeEnum<*> -> it.invoke(Unit) as T
                            else -> throw StorageException("Unknown type for T")
                        }
                    } as T
                }
                else -> (reference as IsStorageBytesEncodable<T>).fromStorageBytes(b, o, l)
            }
        }
    } ?: return false

    if (shouldHandlePrevValue) {
        // Primarily for validations
        handlePreviousValue?.invoke(referenceToCompareTo, prevValue)
    }

    // Do not delete IncMap values since they are needed for incrementing
    // Otherwise delete in normal table
    val shouldNotDeleteCompletely = reference is IsPropertyReferenceWithParent<*, *, *, *> && reference.parentReference is IncMapReference<*, *, *>

    // Delete value and complex sub parts below same reference
    val iterator = transaction.getIterator(readOptions, columnFamilies.table)
    iterator.seek(referenceToCompareTo)
    val refOfParent = referenceOfParent
    while (iterator.isValid()) {
        val ref = iterator.key()

        if (ref.matchesRangePart(0, referenceToCompareTo)) {
            // Delete if not a list or no further list items
            if (toShiftListCount <= 0u) {
                if (shouldNotDeleteCompletely) {
                    setValue(transaction, columnFamilies, ref, version, TypeIndicator.DeletedIndicator.byteArray)
                } else {
                    deleteValue(transaction, columnFamilies, ref, version)
                }
            }
        } else if (refOfParent != null && ref.matchesRangePart(0, refOfParent)) {
            // To handle list shifting
            if (toShiftListCount > 0u) {
                val value = iterator.value()
                setValue(transaction, columnFamilies, ref.prevByteInSameLength(), version, value, VERSION_BYTE_SIZE, value.size - VERSION_BYTE_SIZE)
                toShiftListCount--
            }

            if (toShiftListCount <= 0u) {
                deleteValue(transaction, columnFamilies, ref, version)
            }
        } else {
            break
        }
        iterator.next()
    }

    return true
}
