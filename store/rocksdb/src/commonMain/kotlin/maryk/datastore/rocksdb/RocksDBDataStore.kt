package maryk.datastore.rocksdb

import kotlinx.coroutines.channels.SendChannel
import maryk.core.exceptions.DefNotFoundException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.datastore.rocksdb.TableType.HistoricIndex
import maryk.datastore.rocksdb.TableType.HistoricTable
import maryk.datastore.rocksdb.TableType.HistoricUnique
import maryk.datastore.rocksdb.TableType.Index
import maryk.datastore.rocksdb.TableType.Keys
import maryk.datastore.rocksdb.TableType.Model
import maryk.datastore.rocksdb.TableType.Table
import maryk.datastore.rocksdb.TableType.Unique
import maryk.datastore.rocksdb.processors.TRUE_ARRAY
import maryk.datastore.rocksdb.processors.VersionedComparator
import maryk.datastore.shared.AbstractDataStore
import maryk.datastore.shared.StoreAction
import maryk.rocksdb.ColumnFamilyDescriptor
import maryk.rocksdb.ColumnFamilyHandle
import maryk.rocksdb.ColumnFamilyOptions
import maryk.rocksdb.DBOptions
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.RocksDB
import maryk.rocksdb.WriteOptions
import maryk.rocksdb.defaultColumnFamily
import maryk.rocksdb.openRocksDB
import maryk.rocksdb.use

internal typealias StoreExecutor = Unit.(StoreAction<*, *, *, *>, RocksDBDataStore) -> Unit
internal typealias StoreActor = SendChannel<StoreAction<*, *, *, *>>

class RocksDBDataStore(
    override val keepAllVersions: Boolean = true,
    relativePath: String,
    dataModelsById: Map<UInt, RootDataModel<*, *>>,
    rocksDBOptions: DBOptions? = null
) : AbstractDataStore(dataModelsById) {
    private val columnFamilyHandlesByDataModelIndex = mutableMapOf<UInt, TableColumnFamilies>()
    private val prefixSizesByColumnFamilyHandlesIndex = mutableMapOf<Int, Int>()
    private val uniqueIndicesByDataModelIndex = mutableMapOf<UInt, List<ByteArray>>()

    // Only create Options if no Options were passed. Will take ownership and close it if this object is closed
    private val ownRocksDBOptions: DBOptions? = if (rocksDBOptions == null) DBOptions() else null

    internal val db: RocksDB

    private val storeActor = this.storeActor(this, storeExecutor)

    private val defaultWriteOptions = WriteOptions()
    internal val defaultReadOptions = ReadOptions().apply {
        setPrefixSameAsStart(true)
    }

    init {
        val descriptors: MutableList<ColumnFamilyDescriptor> = mutableListOf()
        descriptors.add(ColumnFamilyDescriptor(defaultColumnFamily))
        for ((index, db) in dataModelsById) {
            createColumnFamilyHandles(descriptors, index, db)
        }

        val handles = mutableListOf<ColumnFamilyHandle>()
        this.db = openRocksDB(rocksDBOptions ?: ownRocksDBOptions!!, relativePath, descriptors, handles)

        var handleIndex = 1
        if (keepAllVersions) {
            for ((index, db) in dataModelsById) {
                prefixSizesByColumnFamilyHandlesIndex[handles[handleIndex+2].getID()] = db.keyByteSize
                prefixSizesByColumnFamilyHandlesIndex[handles[handleIndex+5].getID()] = db.keyByteSize
                columnFamilyHandlesByDataModelIndex[index] = HistoricTableColumnFamilies(
                    model = handles[handleIndex++],
                    keys = handles[handleIndex++],
                    table = handles[handleIndex++],
                    index = handles[handleIndex++],
                    unique = handles[handleIndex++],
                    historic = BasicTableColumnFamilies(
                        table = handles[handleIndex++],
                        index = handles[handleIndex++],
                        unique = handles[handleIndex++]
                    )
                )
            }
        } else {
            for ((index, db) in dataModelsById) {
                prefixSizesByColumnFamilyHandlesIndex[handles[handleIndex+2].getID()] = db.keyByteSize
                columnFamilyHandlesByDataModelIndex[index] = TableColumnFamilies(
                    model = handles[handleIndex++],
                    keys = handles[handleIndex++],
                    table = handles[handleIndex++],
                    index = handles[handleIndex++],
                    unique = handles[handleIndex++]
                )
            }
        }
    }

    private fun createColumnFamilyHandles(descriptors: MutableList<ColumnFamilyDescriptor>, tableIndex: UInt, db: RootDataModel<*, *>) {
        val nameSize = tableIndex.calculateVarByteLength() + 1

        // Prefix set to key size for more optimal search.
        val tableOptions = ColumnFamilyOptions().apply {
            useFixedLengthPrefixExtractor(db.keyByteSize)
        }

        descriptors += Model.getDescriptor(tableIndex, nameSize)
        descriptors += Keys.getDescriptor(tableIndex, nameSize)
        descriptors += Table.getDescriptor(tableIndex, nameSize, tableOptions)
        descriptors += Index.getDescriptor(tableIndex, nameSize)
        descriptors += Unique.getDescriptor(tableIndex, nameSize)

        if (keepAllVersions) {
            val comparator = VersionedComparator(db.keyByteSize)
            // Prefix set to key size for more optimal search.
            val tableOptionsHistoric = ColumnFamilyOptions().apply {
                useFixedLengthPrefixExtractor(db.keyByteSize)
                setComparator(comparator)
            }

            // Prefix set to key size for more optimal search.
            val indexOptionsHistoric = ColumnFamilyOptions().apply {
                setComparator(comparator)
            }

            descriptors += HistoricTable.getDescriptor(tableIndex, nameSize, tableOptionsHistoric)
            descriptors += HistoricIndex.getDescriptor(tableIndex, nameSize, indexOptionsHistoric)
            descriptors += HistoricUnique.getDescriptor(tableIndex, nameSize, indexOptionsHistoric)
        }
    }

    internal fun getPrefixSize(columnFamilyHandle: ColumnFamilyHandle) =
        this.prefixSizesByColumnFamilyHandlesIndex.getOrElse(columnFamilyHandle.getID()) { 0 }

    override fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> getStoreActor(dataModel: DM) =
        this.storeActor

    override fun close() {
        super.close()
        storeActor.close()
        db.close()
        ownRocksDBOptions?.close()
        defaultWriteOptions.close()
        defaultReadOptions.close()

        columnFamilyHandlesByDataModelIndex.values.forEach {
            it.close()
        }
    }

    internal fun getColumnFamilies(dbIndex: UInt) =
        columnFamilyHandlesByDataModelIndex[dbIndex]
            ?: throw DefNotFoundException("DataModel definition not found for $dbIndex")

    /** Get the unique indices for [dbIndex] and [uniqueHandle] */
    internal fun getUniqueIndices(dbIndex: UInt, uniqueHandle: ColumnFamilyHandle) =
        uniqueIndicesByDataModelIndex[dbIndex] ?: searchExistingUniqueIndices(uniqueHandle)

    /**
     * Checks if unique index exists and creates it if not otherwise.
     * This is needed so delete knows which indices to scan for values to delete.
     */
    internal fun createUniqueIndexIfNotExists(dbIndex: UInt, uniqueHandle: ColumnFamilyHandle, uniqueName: ByteArray) {
        val existingDbUniques = uniqueIndicesByDataModelIndex[dbIndex] as MutableList<ByteArray>?
            ?: searchExistingUniqueIndices(uniqueHandle).also { uniqueIndicesByDataModelIndex[dbIndex] = it }
        val existingValue = existingDbUniques.find { it.contentEquals(uniqueName) }

        if (existingValue == null) {
            val uniqueReference = byteArrayOf(0, *uniqueName)
            db.put(uniqueHandle, uniqueReference, TRUE_ARRAY)
            existingDbUniques.add(uniqueName)
        }
    }

    /** Search for existing unique indices in data store by [uniqueHandle] */
    private fun searchExistingUniqueIndices(
        uniqueHandle: ColumnFamilyHandle
    ) = mutableListOf<ByteArray>().also { list ->
        this.db.newIterator(uniqueHandle).use { iterator ->
            while (iterator.isValid()) {
                val key = iterator.key()
                if (key[0] != 0.toByte()) {
                    break // break because it is not describing an index
                }
                list += key.copyOfRange(1, key.size)
            }
        }
    }
}
