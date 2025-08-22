package maryk.datastore.foundationdb.processors.helpers

import com.apple.foundationdb.Transaction
import maryk.datastore.foundationdb.IsTableDirectories

internal fun setLatestVersion(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    keyBytes: ByteArray,
    version: ByteArray
) {
    tr.set(tableDirs.table.pack(keyBytes), version)
}
