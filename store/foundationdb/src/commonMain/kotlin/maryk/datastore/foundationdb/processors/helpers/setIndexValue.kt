package maryk.datastore.foundationdb.processors.helpers

import com.apple.foundationdb.Transaction
import maryk.datastore.foundationdb.IsTableDirectories

internal fun setIndexValue(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    indexReference: ByteArray,
    valueAndKeyBytes: ByteArray,
    version: ByteArray
) {
    tr.set(
        packKey(tableDirs.indexPrefix, indexReference, valueAndKeyBytes),
        version
    )
    writeHistoricIndex(tr, tableDirs, indexReference, valueAndKeyBytes, version, ByteArray(0))
}
