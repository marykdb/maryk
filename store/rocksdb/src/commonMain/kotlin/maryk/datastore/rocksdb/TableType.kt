package maryk.datastore.rocksdb

import maryk.core.extensions.bytes.writeVarBytes
import maryk.rocksdb.ColumnFamilyDescriptor
import maryk.rocksdb.ColumnFamilyOptions

internal enum class TableType(
    val byte: Byte
) {
    Model(1),
    Keys(2),
    Table(3),
    Index(4),
    Unique(5),
    HistoricTable(6),
    HistoricIndex(7),
    HistoricUnique(8),
    UpdateHistory(9);

    fun getName(tableIndex: UInt): ByteArray {
        val name = ByteArray(6)
        name[0] = byte
        var index = 1
        tableIndex.writeVarBytes { name[index++] = it }
        return name.copyOf(index)
    }

    fun getDescriptor(tableIndex: UInt, nameSize: Int, options: ColumnFamilyOptions? = null): ColumnFamilyDescriptor {
        val name = getName(tableIndex)

        return options?.let {
            ColumnFamilyDescriptor(name, it)
        } ?: ColumnFamilyDescriptor(name)
    }
}
