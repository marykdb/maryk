package maryk.datastore.foundationdb.processors

import com.apple.foundationdb.Transaction
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.processors.helpers.getValue

/** Check if Object is soft deleted */
internal fun isSoftDeleted(
    transaction: Transaction,
    tableDirectories: IsTableDirectories,
    toVersion: ULong?,
    key: ByteArray,
    keyOffset: Int = 0,
    keyLength: Int = key.size
): Boolean {
    val softDeleteQualifier = ByteArray(keyLength + 1)
    key.copyInto(softDeleteQualifier, 0, keyOffset, keyOffset + keyLength)
    softDeleteQualifier[keyLength] = SOFT_DELETE_INDICATOR

    return transaction.getValue(
        tableDirectories,
        toVersion,
        softDeleteQualifier
    ) { b, o, l ->
        b[o + l - 1] == TRUE
    } == true
}
