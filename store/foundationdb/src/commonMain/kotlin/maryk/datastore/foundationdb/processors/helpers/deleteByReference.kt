package maryk.datastore.foundationdb.processors.helpers

import com.apple.foundationdb.Transaction
import maryk.core.exceptions.RequestException
import maryk.core.models.values
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.references.EmbeddedValuesPropertyRef
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceWithParent
import maryk.core.properties.references.ListItemReference
import maryk.core.properties.references.ListReference
import maryk.core.properties.references.MapReference
import maryk.core.properties.references.MapValueReference
import maryk.core.properties.references.MultiTypePropertyReference
import maryk.core.properties.references.SetItemReference
import maryk.core.properties.references.SetReference
import maryk.core.properties.references.TypedValueReference
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.processors.EMPTY_BYTEARRAY

/**
 * Delete all values under the given [reference] for [key].
 * - Validates previous vs null via [handlePreviousValue].
 * - Maintains unique indexes for comparable unique values.
 * - Writes history tombstones when using [HistoricTableDirectories].
 * Returns true if anything was deleted.
 */
internal fun <T : Any> deleteByReference(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    key: Key<*>,
    reference: IsPropertyReference<T, IsPropertyDefinition<T>, *>,
    referenceBytes: ByteArray,
    versionBytes: ByteArray,
    handlePreviousValue: ((prevValue: T?) -> Unit)? = null,
): Boolean {
    if (reference is TypedValueReference<*, *, *>) {
        throw RequestException("Type Reference not allowed for deletes. Use the multi type parent.")
    }

    // Special cases for collection item deletions
    when (reference) {
        is MapValueReference<*, *, *> -> {
            @Suppress("UNCHECKED_CAST")
            val parentRef = reference.parentReference as IsPropertyReference<Map<*, *>, IsPropertyDefinition<Map<*, *>>, *>
            // Decrement count
            withCountUpdate(tr, tableDirs, key, parentRef, -1, versionBytes) { _ -> }

            // Collect current values under this map value and delete or mark deleted
            val current = getCurrentValuesForPrefix(tr, tableDirs, key, referenceBytes)
            val isIncMap = reference.parentReference is maryk.core.properties.references.IncMapReference<*, *, *>
            var changed = false
            for ((qualifier, _) in current) {
                if (isIncMap && qualifier.contentEquals(referenceBytes)) {
                    // Keep record for incrementing map; mark as deleted instead of clearing
                    setValue(tr, tableDirs, key.bytes, qualifier, versionBytes, maryk.datastore.shared.TypeIndicator.DeletedIndicator.byteArray)
                } else {
                    tr.clear(packKey(tableDirs.tablePrefix, key.bytes, qualifier))
                    writeHistoricTable(tr, tableDirs, key.bytes, referenceBytes, versionBytes, EMPTY_BYTEARRAY)
                }
                changed = true
            }
            return changed
        }
        is ListItemReference<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            val listRef = reference.parentReference as ListReference<Any, *>
            val original = tr.getList(tableDirs, key, listRef)
            if (reference.index >= original.size.toUInt()) {
                // Nothing to delete
                return false
            }
            val newList = original.toMutableList().apply { removeAt(reference.index.toInt()) }
            // Validate list size/content before writing
            @Suppress("UNCHECKED_CAST")
            listRef.propertyDefinition.validate(
                previousValue = original,
                newValue = newList,
                parentRefFactory = {
                    (listRef as IsPropertyReferenceWithParent<Any, *, *, *>).parentReference
                }
            )
            val changed = setListValue(tr, tableDirs, key, listRef, newList, original.size, versionBytes)
            return changed
        }
        is SetItemReference<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            val setRef = reference.parentReference as SetReference<Any, IsPropertyContext>
            // Decrement count
            withCountUpdate(tr, tableDirs, key, setRef, -1, versionBytes) { newCount ->
                setRef.propertyDefinition.validateSize(newCount) { setRef }
            }

            // Delete single item
            tr.clear(packKey(tableDirs.tablePrefix, key.bytes, referenceBytes))
            writeHistoricTable(tr, tableDirs, key.bytes, referenceBytes, versionBytes, EMPTY_BYTEARRAY)
            return true
        }
        else -> {}
    }

    // Collect current values under this prefix
    val current = getCurrentValuesForPrefix(tr, tableDirs, key, referenceBytes)

    // If there is a direct value at top (exact qualifier), compute a synthetic prev value for complex types
    current.firstOrNull { it.first.contentEquals(referenceBytes) }?.let { (_, prev) ->
        val syntheticPrev: Any = when (reference) {
            is MapReference<*, *, *> -> mapOf<Any, Any>()
            is ListReference<*, *> -> listOf<Any>()
            is SetReference<*, *> -> setOf<Any>()
            is EmbeddedValuesPropertyRef<*, *> ->
                reference.propertyDefinition.definition.dataModel.values { maryk.core.values.EmptyValueItems }
            is MultiTypePropertyReference<*, *, *, *, *> -> {
                var ri = VERSION_BYTE_SIZE
                val read = maryk.datastore.shared.readValue(reference.comparablePropertyDefinition, { prev[ri++] }) { prev.size - ri }
                when (read) {
                    is TypedValue<*, *> -> read as Any
                    is MultiTypeEnum<*> -> TypedValue(read, Unit) as Any
                    else -> read as Any
                }
            }
            else -> decodePrevForDelete(reference, prev, VERSION_BYTE_SIZE, prev.size - VERSION_BYTE_SIZE) as Any
        }
        @Suppress("UNCHECKED_CAST")
        handlePreviousValue?.invoke(syntheticPrev as T?)
    }

    // Maintain unique index for top-level comparable unique values
    val def = reference.propertyDefinition
    if (def is IsComparableDefinition<*, *> && def.unique) {
        val currentTop = tr.get(packKey(tableDirs.tablePrefix, key.bytes, referenceBytes)).join()
        if (currentTop != null) {
            val valueBytes = currentTop.copyOfRange(VERSION_BYTE_SIZE, currentTop.size)
            val uniqueRef = referenceBytes + valueBytes
            tr.clear(packKey(tableDirs.uniquePrefix, uniqueRef))
            writeHistoricUnique(tr, tableDirs, key.bytes, uniqueRef, versionBytes)
        }
    }

    // Delete current values in normal table and write tombstones in history
    deletePrefixWithTombstones(tr, tableDirs, key, referenceBytes, versionBytes)

    return current.isNotEmpty()
}
