package maryk.datastore.foundationdb.processors.helpers

import com.apple.foundationdb.Transaction
import maryk.core.extensions.bytes.invert
import maryk.core.properties.types.Key
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.IsTableDirectories

/**
 * For all [currentValues] under [key], clear those not present in [qualifiersToKeep].
 * Also writes historic tombstones when using [HistoricTableDirectories].
 */
internal fun unsetNonChangedValues(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    key: Key<*>,
    currentValues: List<Pair<ByteArray, ByteArray>>,
    qualifiersToKeep: List<ByteArray>,
    versionBytes: ByteArray,
) {
    val qualifierSet = qualifiersToKeep.toMutableList()
    for ((qualifier, _) in currentValues) {
        val keep = qualifierSet.any { it.contentEquals(qualifier) }
        if (!keep) {
            tr.clear(packKey(tableDirs.tablePrefix, key.bytes, qualifier))
            if (tableDirs is HistoricTableDirectories) {
                // Add a tombstone to history for removed entries
                val inv = versionBytes.copyOf().also { it.invert() }
                tr.set(packKey(tableDirs.historicTablePrefix, key.bytes, qualifier, inv), byteArrayOf())
            }
        }
    }
}

