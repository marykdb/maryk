package maryk.datastore.foundationdb.processors.helpers

import maryk.foundationdb.Transaction
import maryk.datastore.foundationdb.IsTableDirectories

internal fun setValue(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    keyBytes: ByteArray,
    reference: ByteArray,
    version: ByteArray,
    value: ByteArray
) {
    // Latest table value: (key, reference) -> (version || value)
    tr.set(
        packKey(tableDirs.tablePrefix, keyBytes, reference),
        concatArrays(version, value)
    )
    writeHistoricTable(tr, tableDirs, keyBytes, reference, version, value)
}
