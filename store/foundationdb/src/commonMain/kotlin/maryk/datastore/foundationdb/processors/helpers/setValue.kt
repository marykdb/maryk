package maryk.datastore.foundationdb.processors.helpers

import com.apple.foundationdb.Transaction
import maryk.core.extensions.bytes.invert
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.lib.bytes.combineToByteArray

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
        combineToByteArray(version, value)
    )
    // Historic table: (key, reference, invertedVersion) -> value
    if (tableDirs is HistoricTableDirectories) {
        val inv = version.copyOf()
        inv.invert()
        tr.set(
            packKey(tableDirs.historicTablePrefix, keyBytes, reference, inv),
            value
        )
    }
}
