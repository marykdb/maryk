package maryk.datastore.rocksdb

import maryk.rocksdb.ColumnFamilyHandle

internal class HistoricTableColumnFamilies(
    model: ColumnFamilyHandle,
    keys: ColumnFamilyHandle,
    table: ColumnFamilyHandle,
    index: ColumnFamilyHandle,
    unique: ColumnFamilyHandle,
    updateHistory: ColumnFamilyHandle? = null,
    val historic: BasicTableColumnFamilies
) : TableColumnFamilies(model, keys, table, index, unique, updateHistory) {
    override fun close() {
        super.close()
        historic.close()
    }
}
