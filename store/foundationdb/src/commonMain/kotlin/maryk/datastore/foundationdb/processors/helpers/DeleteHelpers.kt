package maryk.datastore.foundationdb.processors.helpers

import maryk.foundationdb.Transaction
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.invoke
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.processors.EMPTY_BYTEARRAY
import maryk.datastore.shared.helpers.convertToValue

/** Delete all current values for [referencePrefix] and write historic tombstones where applicable. */
internal fun deletePrefixWithTombstones(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    key: Key<*>,
    referencePrefix: ByteArray,
    version: ByteArray
) {
    val current = getCurrentValuesForPrefix(tr, tableDirs, key, referencePrefix)
    for ((qualifier, _) in current) {
        tr.clear(packKey(tableDirs.tablePrefix, key.bytes, qualifier))
        writeHistoricTable(tr, tableDirs, key.bytes, qualifier, version, EMPTY_BYTEARRAY)
    }
}

/**
 * Decode a previous value for delete validation, synthesizing for complex types when needed.
 */
internal fun decodePrevForDelete(
    reference: IsPropertyReference<*, *, *>,
    prevBytes: ByteArray,
    offset: Int,
    length: Int
): Any? {
    return try {
        prevBytes.convertToValue(reference, offset, length)
    } catch (_: Throwable) {
        // For multi-type cases, convert enum-only reads into TypedValue(enum, Unit)
        var ri = offset
        val read = maryk.datastore.shared.readValue(reference.comparablePropertyDefinition, { prevBytes[ri++] }) { offset + length - ri }
        when (read) {
            is TypedValue<*, *> -> read
            is MultiTypeEnum<*> -> read(Unit)
            else -> read
        }
    }
}
