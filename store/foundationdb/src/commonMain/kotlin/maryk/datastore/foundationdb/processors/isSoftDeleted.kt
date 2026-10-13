package maryk.datastore.foundationdb.processors

import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.processors.helpers.DecryptValue
import maryk.datastore.foundationdb.processors.helpers.getValue
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.foundationdb.processors.helpers.readVersionBytes
import maryk.foundationdb.Transaction

/** Check if Object is soft deleted */
internal fun isSoftDeleted(
    transaction: Transaction,
    tableDirectories: IsTableDirectories,
    toVersion: ULong?,
    key: ByteArray,
    keyOffset: Int = 0,
    keyLength: Int = key.size,
    decryptValue: DecryptValue? = null
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
    ) { b, o, l ->
        if (l != 1) return@getValue false
        b[o + l - 1] == TRUE
    } == true
}
