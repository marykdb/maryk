package maryk.datastore.rocksdb

import maryk.core.extensions.bytes.writeVarBytes
import maryk.rocksdb.ColumnFamilyDescriptor
import maryk.rocksdb.ColumnFamilyOptions

internal enum class TableType(
    val byte: Byte
) {
    Model(0),
    Keys(1),
    Table(2),
    Index(3),
    Unique(4),
    HistoricTable(5),
    HistoricIndex(6),
    HistoricUnique(7);

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
