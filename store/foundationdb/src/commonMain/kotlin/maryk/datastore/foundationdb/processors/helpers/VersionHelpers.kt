package maryk.datastore.foundationdb.processors.helpers

import maryk.core.clock.HLC
import maryk.core.exceptions.StorageException
import maryk.core.properties.types.Key
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.foundationdb.Transaction

internal fun ByteArray.readHLCTimestampIfPresent(offset: Int = 0): ULong? {
    return if (offset >= 0 && size - offset >= VERSION_BYTE_SIZE) {
        HLC.fromStorageBytes(this, offset).timestamp
    } else {
        null
    }
}

/** Read last stored version timestamp for a key. Returns 0u if absent. */
internal fun getLastVersion(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    key: Key<*>
): ULong {
    val latest = tr.get(packKey(tableDirs.tablePrefix, key.bytes)).awaitResult() ?: return 0u
    if (latest.size != VERSION_BYTE_SIZE) {
        throw StorageException("Invalid stored latest version: ${latest.size} bytes")
    }
    return HLC.fromStorageBytes(latest).timestamp
}
