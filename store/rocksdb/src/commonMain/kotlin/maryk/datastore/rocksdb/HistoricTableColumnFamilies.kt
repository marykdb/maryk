package maryk.datastore.rocksdb

import maryk.rocksdb.ColumnFamilyHandle

class HistoricTableColumnFamilies(
    table: ColumnFamilyHandle,
    index: ColumnFamilyHandle,
    unique: ColumnFamilyHandle,
    val historic: TableColumnFamilies
) : TableColumnFamilies(table, index, unique) {
    override fun close() {
        super.close()
        historic.close()
    }
}
