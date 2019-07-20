package maryk.datastore.rocksdb

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
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
import maryk.datastore.shared.StoreActor
import maryk.rocksdb.ColumnFamilyHandle
import maryk.rocksdb.ColumnFamilyOptions
import maryk.rocksdb.Options
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.TransactionDB
import maryk.rocksdb.TransactionDBOptions
import maryk.rocksdb.WriteOptions
import maryk.rocksdb.openTransactionDB
import maryk.rocksdb.use

internal typealias StoreExecutor = Unit.(StoreAction<*, *, *, *>, RocksDBDataStore) -> Unit
internal typealias StoreActor = SendChannel<StoreAction<*, *, *, *>>

internal expect fun CoroutineScope.storeActor(
    store: RocksDBDataStore,
    executor: StoreExecutor
): StoreActor<*, *>

class RocksDBDataStore(
    override val keepAllVersions: Boolean = true,
    relativePath: String,
    dataModelsById: Map<UInt, RootDataModel<*, *>>,
    rocksDBOptions: Options? = null
) : AbstractDataStore(dataModelsById) {
    override val coroutineContext = GlobalScope.coroutineContext

    private val columnFamilyHandlesByDataModelIndex = mutableMapOf<UInt, TableColumnFamilies>()
    private val uniqueIndicesByDataModelIndex = mutableMapOf<UInt, List<ByteArray>>()

    // Only create Options if no Options were passed. Will take ownership and close it if this object is closed
    private val ownRocksDBOptions: Options? = if (rocksDBOptions == null) Options() else null

    private val transactionDBOptions = TransactionDBOptions()

    internal val db: TransactionDB = openTransactionDB(rocksDBOptions ?: ownRocksDBOptions!!, transactionDBOptions, relativePath)

    private val storeActor = this.storeActor(this, storeExecutor)

    internal val defaultWriteOptions = WriteOptions()
    internal val defaultReadOptions = ReadOptions().apply {
        setPrefixSameAsStart(true)
    }

    init {
        for ((index, db) in dataModelsById) {
            columnFamilyHandlesByDataModelIndex[index] = createColumnFamilyHandles(index, db)
        }
    }

    private fun createColumnFamilyHandles(tableIndex: UInt, db: RootDataModel<*, *>) : TableColumnFamilies {
        val nameSize = tableIndex.calculateVarByteLength() + 1

        // Prefix set to key size for more optimal search.
        val tableOptions = ColumnFamilyOptions().apply {
            useFixedLengthPrefixExtractor(db.keyByteSize)
        }

        val modelDesc = this.db.createColumnFamily(Model.getDescriptor(tableIndex, nameSize))
        val keysDesc = this.db.createColumnFamily(Keys.getDescriptor(tableIndex, nameSize))
        val tableDesc = this.db.createColumnFamily(Table.getDescriptor(tableIndex, nameSize, tableOptions))
        val indexDesc = this.db.createColumnFamily(Index.getDescriptor(tableIndex, nameSize))
        val uniqueDesc = this.db.createColumnFamily(Unique.getDescriptor(tableIndex, nameSize))

        return if (keepAllVersions) {
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

            val historicTableDesc = this.db.createColumnFamily(HistoricTable.getDescriptor(tableIndex, nameSize, tableOptionsHistoric))
            val historicIndexDesc = this.db.createColumnFamily(HistoricIndex.getDescriptor(tableIndex, nameSize, indexOptionsHistoric))
            val historicUniqueDesc = this.db.createColumnFamily(HistoricUnique.getDescriptor(tableIndex, nameSize, indexOptionsHistoric))

            HistoricTableColumnFamilies(
                modelDesc,
                keysDesc,
                tableDesc,
                indexDesc,
                uniqueDesc,
                BasicTableColumnFamilies(
                    historicTableDesc,
                    historicIndexDesc,
                    historicUniqueDesc
                )
            )
        } else {
            TableColumnFamilies(modelDesc, keysDesc, tableDesc, indexDesc, uniqueDesc)
        }
    }

    override fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> getStoreActor(dataModel: DM) =
        this.storeActor

    override fun close() {
        db.close()
        transactionDBOptions.close()
        ownRocksDBOptions?.close()
        defaultWriteOptions.close()
        defaultReadOptions.close()

        columnFamilyHandlesByDataModelIndex.values.forEach {
            it.close()
        }
    }

    fun getColumnFamilies(dbIndex: UInt) =
        columnFamilyHandlesByDataModelIndex[dbIndex]
            ?: throw DefNotFoundException("DataModel definition not found for $dbIndex")

    /** Get the unique indices for [dbIndex] and [uniqueHandle] */
    fun getUniqueIndices(dbIndex: UInt, uniqueHandle: ColumnFamilyHandle) =
        uniqueIndicesByDataModelIndex[dbIndex] ?: searchExistingUniqueIndices(uniqueHandle)

    /**
     * Checks if unique index exists and creates it if not otherwise.
     * This is needed so delete knows which indices to scan for values to delete.
     */
    fun createUniqueIndexIfNotExists(dbIndex: UInt, uniqueHandle: ColumnFamilyHandle, uniqueName: ByteArray) {
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
