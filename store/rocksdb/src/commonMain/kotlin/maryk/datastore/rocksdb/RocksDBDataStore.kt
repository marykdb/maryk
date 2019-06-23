package maryk.datastore.rocksdb

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.SendChannel
import maryk.core.exceptions.DefNotFoundException
import maryk.core.extensions.bytes.calculateVarIntWithExtraInfoByteSize
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.datastore.rocksdb.TableType.Index
import maryk.datastore.rocksdb.TableType.Table
import maryk.datastore.rocksdb.TableType.Unique
import maryk.datastore.rocksdb.processors.TRUE_ARRAY
import maryk.datastore.shared.AbstractDataStore
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.StoreActor
import maryk.rocksdb.ColumnFamilyDescriptor
import maryk.rocksdb.ColumnFamilyHandle
import maryk.rocksdb.Options
import maryk.rocksdb.TransactionDB
import maryk.rocksdb.TransactionDBOptions
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

    init {
        for (index in dataModelsById.keys) {
            columnFamilyHandlesByDataModelIndex[index] = createColumnFamilyHandles(index)
        }
    }

    private fun createColumnFamilyHandles(tableIndex: UInt) : TableColumnFamilies {
        val columnFamilyNameSize = tableIndex.calculateVarIntWithExtraInfoByteSize()

        var index = 0
        val tableTypes = if (keepAllVersions) TableType.values() else arrayOf(Table, Index, Unique)

        val handles = mutableListOf<ColumnFamilyHandle>()

        for (tableType in tableTypes) {
            val name = ByteArray(columnFamilyNameSize)
            tableIndex.writeVarIntWithExtraInfo(tableType.byte) { name[index++] = it }
            index = 0
            handles += db.createColumnFamily(ColumnFamilyDescriptor(name))
        }

        return if (keepAllVersions) {
            HistoricTableColumnFamilies(
                handles[0],
                handles[1],
                handles[2],
                TableColumnFamilies(handles[3], handles[4], handles[5])
            )
        } else {
            TableColumnFamilies(handles[0], handles[1], handles[2])
        }
    }

    override fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> getStoreActor(dataModel: DM) =
        this.storeActor

    override fun close() {
        db.close()
        transactionDBOptions.close()
        ownRocksDBOptions?.close()

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
