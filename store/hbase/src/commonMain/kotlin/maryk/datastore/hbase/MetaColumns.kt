package maryk.datastore.hbase

/**
 * Columns to be used in Metadata column family
 */
enum class MetaColumns(byte: Byte) {
    CreatedVersion(0),
    LatestVersion(1);

    val byteArray = byteArrayOf(0, byte)
}
