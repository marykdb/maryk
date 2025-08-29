package maryk.datastore.foundationdb.processors.helpers

import com.apple.foundationdb.Transaction
import maryk.core.exceptions.RequestException
import maryk.core.extensions.bytes.toVarBytes
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsChangeableValueDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceWithParent
import maryk.core.properties.references.ListAnyItemReference
import maryk.core.properties.references.ListItemReference
import maryk.core.properties.references.MapAnyValueReference
import maryk.core.properties.references.MapKeyReference
import maryk.core.properties.references.MapReference
import maryk.core.properties.references.MapValueReference
import maryk.core.properties.references.SetItemReference
import maryk.core.properties.types.Key
import maryk.datastore.foundationdb.IsTableDirectories

internal fun checkParentReference(
    reference: IsPropertyReference<Any, IsChangeableValueDefinition<Any, IsPropertyContext>, *>
) {
    when (reference) {
        is ListItemReference<*, *> -> throw RequestException("ListItem can only be changed if it exists. To add a new one use ListChange.")
        is MapValueReference<*, *, *> -> { /* ok - parent exists; exact key semantics checked elsewhere */ }
        is SetItemReference<*, *> -> throw RequestException("Not allowed to add with a Set Item reference, use SetChange instead")
        is MapKeyReference<*, *, *> -> throw RequestException("Not allowed to add with a Map key, use Map value instead")
        is MapAnyValueReference<*, *, *> -> throw RequestException("Not allowed to add Map with any key reference")
        is ListAnyItemReference<*, *> -> throw RequestException("Not allowed to add List with any item reference")
        is IsPropertyReferenceWithParent<*, *, *, *> -> { /* ok */ }
        else -> { /* ok */ }
    }
}

internal fun handleMapAdditionCount(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    key: Key<*>,
    reference: MapValueReference<*, *, *>,
    versionBytes: ByteArray
) {
    @Suppress("UNCHECKED_CAST")
    val mapDef = reference.mapDefinition as IsMapDefinition<Any, Any, IsPropertyContext>
    mapDef.keyDefinition.validateWithRef(reference.key, reference.key) {
        @Suppress("UNCHECKED_CAST")
        mapDef.keyRef(reference.key, reference.parentReference as MapReference<Any, Any, IsPropertyContext>)
    }

    @Suppress("UNCHECKED_CAST")
    val parentRef = reference.parentReference as IsPropertyReference<Map<Any, Any>, IsPropertyDefinition<Map<Any, Any>>, *>
    val countEntry = tr.get(packKey(tableDirs.tablePrefix, key.bytes, parentRef.toStorageByteArray())).join()
    val prevCount = countEntry?.let { arr ->
        var ri = VERSION_BYTE_SIZE
        maryk.core.extensions.bytes.initIntByVar { arr[ri++] }
    } ?: 0
    val newCount = prevCount + 1
    mapDef.validateSize(newCount.toUInt()) { parentRef }
    setValue(tr, tableDirs, key.bytes, parentRef.toStorageByteArray(), versionBytes, newCount.toVarBytes())
}
