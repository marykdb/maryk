package maryk.datastore.rocksdb.processors.helpers

import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.Transaction
import maryk.lib.extensions.compare.matchesRangePart
import maryk.rocksdb.ReadOptions

internal fun getHistoricUniqueReferenceForKey(
    transaction: Transaction,
    columnFamilies: HistoricTableColumnFamilies?,
    readOptions: ReadOptions,
    reference: ByteArray,
    key: ByteArray
): ByteArray? {
    if (columnFamilies == null) return null

    transaction.getIterator(readOptions, columnFamilies.historic.unique).use { iterator ->
        iterator.seek(reference)

        while (iterator.isValid()) {
            val historicKey = iterator.key()
            if (!historicKey.matchesRangePart(0, reference)) break

            val historicValue = iterator.value()
            if (historicValue.contentEquals(key)) {
                return historicKey.copyOf(historicKey.size - VERSION_BYTE_SIZE)
            }

            iterator.next()
        }
    }

    return null
}
