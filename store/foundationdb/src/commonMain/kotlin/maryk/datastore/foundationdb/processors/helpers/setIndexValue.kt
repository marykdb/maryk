package maryk.datastore.foundationdb.processors.helpers

import com.apple.foundationdb.Transaction
import maryk.core.extensions.bytes.invert
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.IsTableDirectories

internal fun setIndexValue(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    indexReference: ByteArray,
    valueAndKeyBytes: ByteArray,
    version: ByteArray
) {
    // index: (indexRef, valueAndKey) -> version
    tr.set(
        packKey(tableDirs.index, indexReference, valueAndKeyBytes),
        version
    )
    if (tableDirs is HistoricTableDirectories) {
        val inv = version.copyOf()
        inv.invert()
        tr.set(
            packKey(tableDirs.historicIndex, indexReference, valueAndKeyBytes, inv),
            ByteArray(0)
        )
    }
}
