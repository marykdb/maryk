package maryk.datastore.foundationdb.processors.helpers

import maryk.foundationdb.Transaction
import maryk.datastore.foundationdb.IsTableDirectories

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
        concatArrays(version, keyBytes)
    )
    writeHistoricUnique(tr, tableDirs, keyBytes, uniqueReferenceWithValue, version)
}
