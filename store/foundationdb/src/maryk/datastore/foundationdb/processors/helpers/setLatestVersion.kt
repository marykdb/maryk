package maryk.datastore.foundationdb.processors.helpers

import maryk.foundationdb.Transaction
import maryk.datastore.foundationdb.IsTableDirectories

internal fun setLatestVersion(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    keyBytes: ByteArray,
    version: ByteArray
) {
    tr.set(packKey(tableDirs.tablePrefix, keyBytes), version)
}
