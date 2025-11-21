package maryk.datastore.foundationdb.processors.helpers

import maryk.foundationdb.Transaction
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.IsTableDirectories

fun setCreatedVersion(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    keyBytes: ByteArray,
    version: ByteArray
) {
    tr.set(packKey(tableDirs.keysPrefix, keyBytes), version)
    tr.set(packKey(tableDirs.tablePrefix, keyBytes), version)
    if (tableDirs is HistoricTableDirectories) {
        tr.set(packKey(tableDirs.historicTablePrefix, keyBytes), version)
    }
}
