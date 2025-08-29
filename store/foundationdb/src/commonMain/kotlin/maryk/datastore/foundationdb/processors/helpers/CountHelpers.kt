package maryk.datastore.foundationdb.processors.helpers

import com.apple.foundationdb.Transaction
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.toVarBytes
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Key
import maryk.datastore.foundationdb.IsTableDirectories

/** Read the container count (varint) for a parent reference. Returns 0 if absent. */
internal fun getContainerCount(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    key: Key<*>,
    parentRef: IsPropertyReference<*, *, *>
): Int {
    val packed = tr.get(packKey(tableDirs.tablePrefix, key.bytes, parentRef.toStorageByteArray())).join()
    return packed?.let { arr ->
        var ri = VERSION_BYTE_SIZE
        initIntByVar { arr[ri++] }
    } ?: 0
}

/** Write the container count (varint) for a parent reference. */
internal fun setContainerCount(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    key: Key<*>,
    parentRef: IsPropertyReference<*, *, *>,
    newCount: Int,
    version: ByteArray
) {
    setValue(tr, tableDirs, key.bytes, parentRef.toStorageByteArray(), version, newCount.toVarBytes())
}

/**
 * Update the container count by [delta] and validate via [validate] before writing.
 * The [validate] callback is given the new count as UInt so size constraints can be applied.
 */
internal fun withCountUpdate(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    key: Key<*>,
    parentRef: IsPropertyReference<*, *, *>,
    delta: Int,
    version: ByteArray,
    validate: (UInt) -> Unit
) {
    val prev = getContainerCount(tr, tableDirs, key, parentRef)
    val next = (prev + delta).coerceAtLeast(0)
    validate(next.toUInt())
    setContainerCount(tr, tableDirs, key, parentRef, next, version)
}
