package maryk.datastore.rocksdb

import org.rocksdb.ColumnFamilyHandle

internal class HistoricTableColumnFamilies(
    model: ColumnFamilyHandle,
    keys: ColumnFamilyHandle,
    table: ColumnFamilyHandle,
    index: ColumnFamilyHandle,
    unique: ColumnFamilyHandle,
    val historic: BasicTableColumnFamilies
) : TableColumnFamilies(model, keys, table, index, unique) {
    override fun close() {
        super.close()
        historic.close()
    }
}
