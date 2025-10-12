package maryk.datastore.foundationdb.processors.helpers

import com.apple.foundationdb.Transaction
import maryk.core.clock.HLC
import maryk.core.properties.types.Key
import maryk.datastore.foundationdb.IsTableDirectories

/** Read last stored version timestamp for a key. Returns 0u if absent. */
internal fun getLastVersion(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    key: Key<*>
): ULong {
    val latest = tr.get(packKey(tableDirs.tablePrefix, key.bytes)).awaitResult() ?: return 0u
    return HLC.fromStorageBytes(latest).timestamp
}
