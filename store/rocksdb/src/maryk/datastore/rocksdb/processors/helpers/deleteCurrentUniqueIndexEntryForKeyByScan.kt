package maryk.datastore.rocksdb.processors.helpers

import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.Transaction
import maryk.lib.extensions.compare.matchesRangePart
import maryk.rocksdb.ReadOptions

internal fun deleteCurrentUniqueIndexEntryForKeyByScan(
    transaction: Transaction,
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    reference: ByteArray,
    key: ByteArray,
    versionBytes: ByteArray
) {
    transaction.getIterator(readOptions, columnFamilies.unique).use { iterator ->
        iterator.seek(reference)

        while (iterator.isValid()) {
            val uniqueReference = iterator.key()
            if (!uniqueReference.matchesRangePart(0, reference)) {
                break
            }

            val value = iterator.value()
            if (
                value.size == VERSION_BYTE_SIZE + key.size &&
                value.matchesRangePart(VERSION_BYTE_SIZE, key)
            ) {
                deleteUniqueIndexValue(
                    transaction = transaction,
                    columnFamilies = columnFamilies,
                    indexReference = reference,
                    value = uniqueReference,
                    valueOffset = reference.size,
                    valueLength = uniqueReference.size - reference.size,
                    version = versionBytes,
                    hardDelete = false,
                    historicValue = key
                )
                break
            }

            iterator.next()
        }
    }
}
