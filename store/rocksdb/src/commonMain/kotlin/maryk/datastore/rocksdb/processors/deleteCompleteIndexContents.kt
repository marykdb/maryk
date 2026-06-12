package maryk.datastore.rocksdb.processors

import maryk.core.properties.definitions.index.IsIndexable
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.Transaction
import maryk.lib.extensions.compare.nextByteInSameLength

/**
 * Deletes all index values in [transaction] at [tableColumnFamilies] for [indexable]
 */
internal fun deleteCompleteIndexContents(transaction: Transaction, tableColumnFamilies: TableColumnFamilies, indexable: IsIndexable) {
    val indexEndStorageReference = indexable.referenceStorageByteArray.bytes.nextByteInSameLength()

    transaction.deleteRange(
        tableColumnFamilies.index,
        indexable.referenceStorageByteArray.bytes,
        indexEndStorageReference
    )

    if (tableColumnFamilies is HistoricTableColumnFamilies) {
        transaction.deleteRange(
            tableColumnFamilies.historic.index,
            indexable.referenceStorageByteArray.bytes,
            indexEndStorageReference
        )
    }
}
