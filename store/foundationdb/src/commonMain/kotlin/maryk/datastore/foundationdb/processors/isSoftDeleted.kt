package maryk.datastore.foundationdb.processors

import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.processors.helpers.DecryptValue
import maryk.datastore.foundationdb.processors.helpers.handleCurrentValue
import maryk.datastore.foundationdb.processors.helpers.getValue
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.foundationdb.processors.helpers.readVersionBytes
import maryk.foundationdb.Transaction
import maryk.foundationdb.FdbFuture

/** Check if Object is soft deleted */
internal fun isSoftDeleted(
    transaction: Transaction,
    tableDirectories: IsTableDirectories,
    toVersion: ULong?,
    key: ByteArray,
    keyOffset: Int = 0,
    keyLength: Int = key.size,
    decryptValue: DecryptValue? = null,
    preparedValue: FdbFuture<ByteArray?>? = null,
): Boolean {
    val softDeleteQualifier = ByteArray(keyLength + 1)
    key.copyInto(softDeleteQualifier, 0, keyOffset, keyOffset + keyLength)
    softDeleteQualifier[keyLength] = SOFT_DELETE_INDICATOR

    // A legacy current marker can be newer than the retained historic marker.
    // Never use it for a snapshot before the marker's own timestamp.
    if (toVersion != null && tableDirectories is HistoricTableDirectories) {
        val current = transaction.get(packKey(tableDirectories.tablePrefix, softDeleteQualifier)).awaitResult()
        if (current != null && current.size == ULong.SIZE_BYTES + 1 && current.readVersionBytes() <= toVersion) {
            return current.last() == TRUE
        }
    }

    return transaction.getValue(
        tableDirectories,
        toVersion,
        softDeleteQualifier,
        keyLength,
        decryptValue = decryptValue,
        preparedValue = if (toVersion == null) preparedValue else null,
    ) { b, o, l ->
        if (l != 1) return@getValue false
        b[o + l - 1] == TRUE
    } == true
}

/** Decode an already-awaited current marker with the same payload rules as a point read. */
internal fun isCurrentSoftDeleted(
    value: ByteArray?,
    tableDirectories: IsTableDirectories,
    key: ByteArray,
    decryptValue: DecryptValue?,
): Boolean = value?.handleCurrentValue(
    decryptValue, tableDirectories.modelId, key, byteArrayOf(SOFT_DELETE_INDICATOR),
) { bytes, offset, length -> length == 1 && bytes[offset] == TRUE } == true
