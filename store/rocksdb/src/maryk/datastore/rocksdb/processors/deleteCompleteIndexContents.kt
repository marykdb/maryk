package maryk.datastore.rocksdb.processors

import maryk.core.properties.definitions.index.IsIndexable
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.Transaction
import maryk.datastore.rocksdb.processors.helpers.createIndexKeyPrefix
import maryk.datastore.rocksdb.processors.helpers.createIndexRangeEnd

/**
 * Deletes all index values in [transaction] at [tableColumnFamilies] for [indexable]
 */
internal fun deleteCompleteIndexContents(transaction: Transaction, tableColumnFamilies: TableColumnFamilies, indexable: IsIndexable) {
    val indexReference = indexable.referenceStorageByteArray.bytes
    val indexStorageReference = createIndexKeyPrefix(indexReference)
    val indexEndStorageReference = createIndexRangeEnd(indexReference)

    transaction.deleteRange(
        tableColumnFamilies.index,
        indexStorageReference,
        indexEndStorageReference
    )

    if (tableColumnFamilies is HistoricTableColumnFamilies) {
        transaction.deleteRange(
            tableColumnFamilies.historic.index,
            indexStorageReference,
            indexEndStorageReference
        )
    }
}
