package maryk.datastore.foundationdb.processors.helpers

import com.apple.foundationdb.Transaction
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.IsTableDirectories

fun setCreatedVersion(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    keyBytes: ByteArray,
    version: ByteArray
) {
    tr.set(tableDirs.keys.pack(keyBytes), version)
    tr.set(tableDirs.table.pack(keyBytes), version)
    if (tableDirs is HistoricTableDirectories) {
        tr.set(tableDirs.historicTable.pack(keyBytes), version)
    }
}
