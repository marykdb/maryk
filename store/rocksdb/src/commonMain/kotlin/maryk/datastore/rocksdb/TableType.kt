package maryk.datastore.rocksdb

enum class TableType(
    val byte: Byte
) {
    Table(0),
    Index(1),
    Unique(2),
    HistoricTable(3),
    HistoricIndex(4),
    HistoricUnique(5)
}
