package maryk.datastore.foundationdb.processors.helpers

import com.apple.foundationdb.Transaction
import maryk.core.extensions.bytes.invert
import maryk.datastore.foundationdb.HistoricTableDirectories
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
        packKey(tableDirs.unique, uniqueReferenceWithValue),
        combineToByteArray(version, keyBytes)
    )
    if (tableDirs is HistoricTableDirectories) {
        val inv = version.copyOf()
        inv.invert()
        tr.set(
            packKey(tableDirs.historicUnique, uniqueReferenceWithValue, inv),
            keyBytes
        )
    }
}
