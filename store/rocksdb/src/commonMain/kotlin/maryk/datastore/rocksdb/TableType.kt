package maryk.datastore.rocksdb

import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.rocksdb.ColumnFamilyDescriptor
import maryk.rocksdb.ColumnFamilyOptions

enum class TableType(
    val byte: Byte
) {
    Table(0),
    Index(1),
    Unique(2),
    HistoricTable(3),
    HistoricIndex(4),
    HistoricUnique(5);

    fun getDescriptor(tableIndex: UInt, nameSize: Int, options: ColumnFamilyOptions? = null): ColumnFamilyDescriptor {
        var index = 0
        val name = ByteArray(nameSize)
        tableIndex.writeVarIntWithExtraInfo(this.byte) { name[index++] = it }

        return options?.let {
            ColumnFamilyDescriptor(name, it)
        } ?: ColumnFamilyDescriptor(name)
    }
}
