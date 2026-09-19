package maryk.datastore.foundationdb.processors.helpers

import maryk.foundationdb.Transaction
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.IsTableDirectories

internal fun writeHistoricTable(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    keyBytes: ByteArray,
    qualifier: ByteArray,
    version: ByteArray,
    value: ByteArray,
) {
    if (tableDirs is HistoricTableDirectories) {
        val encodedQualifier = encodeZeroFreeUsing01(qualifier)
        tr.set(packVersionedKey(tableDirs.historicTablePrefix, keyBytes, encodedQualifier, version = version), value)
    }
}

internal fun writeHistoricUnique(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    keyBytes: ByteArray,
    qualifier: ByteArray,
    version: ByteArray,
) {
    if (tableDirs is HistoricTableDirectories) {
        val encodedQualifier = encodeZeroFreeUsing01(qualifier)
        tr.set(packVersionedKey(tableDirs.historicUniquePrefix, encodedQualifier, version = version), keyBytes)
    }
}

internal fun writeHistoricIndex(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    indexRefBytes: ByteArray,
    keyAndValue: ByteArray,
    version: ByteArray,
    value: ByteArray,
) {
    if (tableDirs is HistoricTableDirectories) {
        // Encode the full qualifier (indexRef || valueAndKey) directly into the final zero-free buffer.
        val encodedQualifier = encodeZeroFreeUsing01(indexRefBytes, keyAndValue)
        tr.set(packVersionedKey(tableDirs.historicIndexPrefix, encodedQualifier, version = version), value)
    }
}
