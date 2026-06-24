package maryk.datastore.foundationdb.processors

import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.processors.helpers.DecryptValue
import maryk.datastore.foundationdb.processors.helpers.getValue
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
