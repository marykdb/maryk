package maryk.datastore.foundationdb.processors.helpers

import com.apple.foundationdb.Transaction
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.lib.bytes.combineToByteArray

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
        // Encode the full qualifier (indexRef || valueAndKey) to be zero-free up to the separator
        val combined = combineToByteArray(indexRefBytes, keyAndValue)
        val encodedQualifier = encodeZeroFreeUsing01(combined)
        tr.set(packVersionedKey(tableDirs.historicIndexPrefix, encodedQualifier, version = version), value)
    }
}
