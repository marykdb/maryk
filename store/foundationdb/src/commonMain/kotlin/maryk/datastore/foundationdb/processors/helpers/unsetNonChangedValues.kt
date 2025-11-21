package maryk.datastore.foundationdb.processors.helpers

import maryk.foundationdb.Transaction
import maryk.core.properties.types.Key
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.processors.EMPTY_BYTEARRAY

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
    val qualifierSet = qualifiersToKeep.mapTo(mutableSetOf()) { it.asByteArrayKey(copy = true) }
    for ((qualifier, _) in currentValues) {
        val keep = qualifierSet.contains(qualifier.asByteArrayKey())
        if (!keep) {
            tr.clear(packKey(tableDirs.tablePrefix, key.bytes, qualifier))
            writeHistoricTable(tr, tableDirs, key.bytes, qualifier, versionBytes, EMPTY_BYTEARRAY)
        }
    }
}
