package maryk.datastore.foundationdb

import com.apple.foundationdb.directory.DirectorySubspace

sealed interface IsTableDirectories {
    val keys: DirectorySubspace
    val model: DirectorySubspace
    val table: DirectorySubspace
    val unique: DirectorySubspace
    val index: DirectorySubspace
}

open class TableDirectories(
    override val model: DirectorySubspace,
    override val keys: DirectorySubspace,
    override val table: DirectorySubspace,
    override val unique: DirectorySubspace,
    override val index: DirectorySubspace,
): IsTableDirectories

class HistoricTableDirectories(
    override val model: DirectorySubspace,
    override val keys: DirectorySubspace,
    override val table: DirectorySubspace,
    override val unique: DirectorySubspace,
    override val index: DirectorySubspace,
    val historicTable: DirectorySubspace,
    val historicUnique: DirectorySubspace,
    val historicIndex: DirectorySubspace,
): IsTableDirectories
