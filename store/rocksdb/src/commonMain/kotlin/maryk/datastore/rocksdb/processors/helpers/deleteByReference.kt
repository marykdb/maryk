package maryk.datastore.rocksdb.processors.helpers

import maryk.core.exceptions.StorageException
import maryk.core.models.emptyValues
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsComparableDefinition
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
import maryk.datastore.shared.hasStoredValuePayload
import maryk.datastore.shared.readValue
import maryk.lib.extensions.compare.matchesRangePart
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
    if (reference is ListItemReference<*, *>) {
        @Suppress("UNCHECKED_CAST")
        val listReference = reference.parentReference as ListReference<Any, *>
        val original = getList(transaction, columnFamilies, readOptions, key, listReference)
        if (reference.index >= original.size.toUInt()) return false
        val updated = original.toMutableList().apply { removeAt(reference.index.toInt()) }
        listReference.propertyDefinition.validateWithRef(original, updated) { listReference }
        return setListValue(transaction, columnFamilies, key, listReference, updated, original.size, version)
    }
    val referenceToCompareTo = key.bytes + referenceAsBytes

    var shouldHandlePrevValue = true

    // Value to delete
    @Suppress("UNCHECKED_CAST")
    val prevValue: T = transaction.getValue(columnFamilies, readOptions, null, referenceToCompareTo) { b, o, l ->
        if (!hasStoredValuePayload(reference.comparablePropertyDefinition, b, o, l)) {
            // does not exist so nothing to delete
            null
        } else {
            // With delete the prev value for complex types needs to be set to check final and required states
            // Only current values are checked on content
            when (reference) {
                is IsMapReference<*, *, *, *> -> mapOf<Any, Any>() as T
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
                            valueDefinition.fromStorageBytes(b, o + 1, l - 1) as T
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
                is SetItemReference<*, *> -> {
                    val setReference = reference.parentReference as SetReference<Any, IsPropertyContext>
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
                    @Suppress("UNCHECKED_CAST")
                    reference.value as T
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
                else -> (reference as IsStorageBytesEncodable<T>).fromStorageBytes(b, o + 1, l - 1)
            }
        }
    } ?: return false

    if (shouldHandlePrevValue) {
        // Primarily for validations
        handlePreviousValue?.invoke(referenceToCompareTo, prevValue)
    }

    if ((reference.comparablePropertyDefinition as? IsComparableDefinition<*, *>)?.unique == true) {
        deleteCurrentUniqueIndexEntryForKeyByScan(
            transaction = transaction,
            columnFamilies = columnFamilies,
            readOptions = readOptions,
            reference = referenceAsBytes,
            key = key.bytes,
            versionBytes = version
        )
    }

    // Do not delete IncMap values since they are needed for incrementing
    // Otherwise delete in normal table
    val shouldNotDeleteCompletely = reference is IsPropertyReferenceWithParent<*, *, *, *> && reference.parentReference is IncMapReference<*, *, *>

    // Delete value and complex sub parts below same reference
    transaction.getIterator(readOptions, columnFamilies.table).use { iterator ->
        iterator.seek(referenceToCompareTo)
        while (iterator.isValid()) {
            val ref = iterator.key()

            if (ref.matchesRangePart(0, referenceToCompareTo)) {
                if (shouldNotDeleteCompletely) {
                    setDeletedValue(transaction, columnFamilies, ref, version)
                } else {
                    deleteValue(transaction, columnFamilies, ref, version)
                }
            } else {
                break
            }
            iterator.next()
        }
    }

    return true
}
