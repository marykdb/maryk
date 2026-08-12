package maryk.datastore.rocksdb

import maryk.rocksdb.ColumnFamilyHandle

internal class HistoricTableColumnFamilies(
    modelId: UInt,
    keyByteSize: Int,
    model: ColumnFamilyHandle,
    keys: ColumnFamilyHandle,
    table: ColumnFamilyHandle,
    index: ColumnFamilyHandle,
    unique: ColumnFamilyHandle,
    updateHistory: ColumnFamilyHandle? = null,
    val historic: BasicTableColumnFamilies
) : TableColumnFamilies(modelId, keyByteSize, model, keys, table, index, unique, updateHistory) {
    override fun close() {
        super.close()
        historic.close()
    }
}
