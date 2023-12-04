package maryk.datastore.rocksdb.processors

import maryk.core.properties.definitions.index.IsIndexable
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.lib.extensions.compare.nextByteInSameLength
import org.rocksdb.RocksDB

/**
 * Deletes all index values in [rocksDB] at [tableColumnFamilies] for [indexable]
 */
internal fun deleteCompleteIndexContents(rocksDB: RocksDB, tableColumnFamilies: TableColumnFamilies, indexable: IsIndexable) {
    val indexEndStorageReference = indexable.referenceStorageByteArray.bytes.nextByteInSameLength()

    rocksDB.deleteRange(
        tableColumnFamilies.index,
        indexable.referenceStorageByteArray.bytes,
        indexEndStorageReference
    )

    if (tableColumnFamilies is HistoricTableColumnFamilies) {
        rocksDB.deleteRange(
            tableColumnFamilies.index,
            indexable.referenceStorageByteArray.bytes,
            indexEndStorageReference
        )
    }
}
