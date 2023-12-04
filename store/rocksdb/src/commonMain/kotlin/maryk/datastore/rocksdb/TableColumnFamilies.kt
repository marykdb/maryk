package maryk.datastore.rocksdb

import org.rocksdb.ColumnFamilyHandle

internal open class TableColumnFamilies(
    val model: ColumnFamilyHandle,
    val keys: ColumnFamilyHandle,
    table: ColumnFamilyHandle,
    index: ColumnFamilyHandle,
    unique: ColumnFamilyHandle
) : BasicTableColumnFamilies(table, index, unique) {
    override fun close() {
        super.close()
        model.close()
        keys.close()
    }
}
