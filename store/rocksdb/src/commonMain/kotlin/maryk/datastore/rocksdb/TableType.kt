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
    HistoricUnique(8);

    fun getDescriptor(tableIndex: UInt, nameSize: Int, options: ColumnFamilyOptions? = null): ColumnFamilyDescriptor {
        var index = 0
        val name = ByteArray(nameSize)
        val writer: (Byte) -> Unit = { name[index++] = it }

        writer(this.byte)
        tableIndex.writeVarBytes(writer)

        return options?.let {
            ColumnFamilyDescriptor(name, it)
        } ?: ColumnFamilyDescriptor(name)
    }
}
