package maryk.datastore.foundationdb.processors.helpers

import com.apple.foundationdb.Transaction
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.lib.bytes.combineToByteArray

internal fun setUniqueIndexValue(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    uniqueReferenceWithValue: ByteArray,
    version: ByteArray,
    keyBytes: ByteArray
) {
    // unique: (uniqueRef||value) -> (version || key)
    tr.set(
        packKey(tableDirs.uniquePrefix, uniqueReferenceWithValue),
        combineToByteArray(version, keyBytes)
    )
    writeHistoricUnique(tr, tableDirs, keyBytes, uniqueReferenceWithValue, version)
}
