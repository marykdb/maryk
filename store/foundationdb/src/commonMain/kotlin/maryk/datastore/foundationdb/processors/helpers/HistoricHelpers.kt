package maryk.datastore.foundationdb.processors.helpers

import com.apple.foundationdb.Transaction
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
        tr.set(packVersionedKey(tableDirs.historicTablePrefix, keyBytes, qualifier, version = version), value)
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
        tr.set(packVersionedKey(tableDirs.historicUniquePrefix, qualifier, version = version), keyBytes)
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
        tr.set(packVersionedKey(tableDirs.historicIndexPrefix, indexRefBytes, keyAndValue, version = version), value)
    }
}
