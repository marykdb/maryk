package maryk.datastore.rocksdb

import maryk.rocksdb.ColumnFamilyHandle

open class BasicTableColumnFamilies(
    val table: ColumnFamilyHandle,
    val index: ColumnFamilyHandle,
    val unique: ColumnFamilyHandle
) {
    open fun close() {
        table.close()
        index.close()
        unique.close()
    }
}
