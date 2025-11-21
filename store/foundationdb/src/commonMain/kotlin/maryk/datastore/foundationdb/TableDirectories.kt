package maryk.datastore.foundationdb

import maryk.foundationdb.directory.DirectorySubspace

sealed interface IsTableDirectories {
    val dataStore: FoundationDBDataStore
    val keysPrefix: ByteArray
    val modelPrefix: ByteArray
    val tablePrefix: ByteArray
    val uniquePrefix: ByteArray
    val indexPrefix: ByteArray
}

open class TableDirectories(
    override val dataStore: FoundationDBDataStore,
    model: DirectorySubspace,
    keys: DirectorySubspace,
    table: DirectorySubspace,
    unique: DirectorySubspace,
    index: DirectorySubspace,
): IsTableDirectories {
    override val keysPrefix = keys.pack()!!
    override val modelPrefix = model.pack()!!
    override val tablePrefix = table.pack()!!
    override val uniquePrefix = unique.pack()!!
    override val indexPrefix = index.pack()!!
}

class HistoricTableDirectories(
    override val dataStore: FoundationDBDataStore,
    model: DirectorySubspace,
    keys: DirectorySubspace,
    table: DirectorySubspace,
    unique: DirectorySubspace,
    index: DirectorySubspace,
    historicTable: DirectorySubspace,
    historicUnique: DirectorySubspace,
    historicIndex: DirectorySubspace,
): IsTableDirectories {
    override val keysPrefix = keys.pack()!!
    override val modelPrefix = model.pack()!!
    override val tablePrefix = table.pack()!!
    override val uniquePrefix = unique.pack()!!
    override val indexPrefix = index.pack()!!
    val historicTablePrefix = historicTable.pack()!!
    val historicUniquePrefix = historicUnique.pack()!!
    val historicIndexPrefix = historicIndex.pack()!!
}
