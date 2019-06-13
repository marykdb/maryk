package maryk.datastore.rocksdb

import maryk.rocksdb.ColumnFamilyHandle

open class TableColumnFamilies(
    val table: ColumnFamilyHandle,
    val index: ColumnFamilyHandle,
    val unique: ColumnFamilyHandle
)
