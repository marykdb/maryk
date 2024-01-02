package maryk.datastore.hbase

/**
 * Columns for the meta data in the TableDescriptor
 */
enum class TableMetaColumns(byte: Byte) {
    Name(0),
    Version(1),
    Model(2),
    Dependents(3);

    val byteArray = byteArrayOf(byte)
}
