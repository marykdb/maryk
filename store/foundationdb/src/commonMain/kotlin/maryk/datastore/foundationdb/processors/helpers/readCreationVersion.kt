package maryk.datastore.foundationdb.processors.helpers

import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.foundationdb.Transaction

internal fun Transaction.readCreationVersion(
    tableDirs: IsTableDirectories,
    keyBytes: ByteArray,
    toVersion: ULong?
): ULong? {
    if (toVersion != null && tableDirs is HistoricTableDirectories) {
        get(packKey(tableDirs.historicTablePrefix, keyBytes)).awaitResult()
            ?.readHLCTimestampIfExact()
            ?.let { return it }
    }

    return get(packKey(tableDirs.keysPrefix, keyBytes)).awaitResult()
        ?.readHLCTimestampIfExact()
}
