package maryk.datastore.foundationdb.processors.helpers

import com.apple.foundationdb.Transaction
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.IsTableDirectories

internal fun writeHistoricTableTombstone(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    keyBytes: ByteArray,
    qualifier: ByteArray,
    invVersion: ByteArray
) {
    if (tableDirs is HistoricTableDirectories) {
        tr.set(packKey(tableDirs.historicTablePrefix, keyBytes, qualifier, invVersion), byteArrayOf())
    }
}

internal fun writeHistoricIndexTombstone(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    indexRefBytes: ByteArray,
    keyAndValue: ByteArray,
    invVersion: ByteArray
) {
    if (tableDirs is HistoricTableDirectories) {
        tr.set(packKey(tableDirs.historicIndexPrefix, indexRefBytes, keyAndValue, invVersion), byteArrayOf())
    }
}
