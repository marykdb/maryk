package maryk.datastore.rocksdb

import kotlinx.coroutines.channels.SendChannel
import maryk.core.exceptions.DefNotFoundException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.models.IsRootDataModel
import maryk.core.models.RootDataModel
import maryk.core.models.migration.MigrationException
import maryk.core.models.migration.MigrationHandler
import maryk.core.models.migration.MigrationStatus.NeedsMigration
import maryk.core.models.migration.MigrationStatus.NewIndicesOnExistingProperties
import maryk.core.models.migration.MigrationStatus.NewModel
import maryk.core.models.migration.MigrationStatus.OnlySafeAdds
import maryk.core.models.migration.MigrationStatus.UpToDate
import maryk.core.models.migration.StoredRootDataModel
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.types.Key
import maryk.datastore.rocksdb.TableType.HistoricIndex
import maryk.datastore.rocksdb.TableType.HistoricTable
import maryk.datastore.rocksdb.TableType.HistoricUnique
import maryk.datastore.rocksdb.TableType.Index
import maryk.datastore.rocksdb.TableType.Keys
import maryk.datastore.rocksdb.TableType.Model
import maryk.datastore.rocksdb.TableType.Table
import maryk.datastore.rocksdb.TableType.Unique
import maryk.datastore.rocksdb.model.checkModelIfMigrationIsNeeded
import maryk.datastore.rocksdb.model.storeModelDefinition
import maryk.datastore.rocksdb.processors.EMPTY_ARRAY
import maryk.datastore.rocksdb.processors.VersionedComparator
import maryk.datastore.rocksdb.processors.deleteCompleteIndexContents
import maryk.datastore.shared.AbstractDataStore
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.updates.Update
import maryk.rocksdb.ColumnFamilyDescriptor
import maryk.rocksdb.ColumnFamilyHandle
import maryk.rocksdb.ColumnFamilyOptions
import maryk.rocksdb.ComparatorOptions
import maryk.rocksdb.DBOptions
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.RocksDB
import maryk.rocksdb.WriteOptions
import maryk.rocksdb.defaultColumnFamily
import maryk.rocksdb.openRocksDB
import maryk.rocksdb.use

internal typealias StoreExecutor = suspend Unit.(StoreAction<*, *, *, *>, RocksDBDataStore, SendChannel<Update<*, *>>) -> Unit

class RocksDBDataStore(
    override val keepAllVersions: Boolean = true,
    relativePath: String,
    dataModelsById: Map<UInt, RootDataModel<*, *>>,
    rocksDBOptions: DBOptions? = null,
    private val onlyCheckModelVersion: Boolean = false,
    val migrationHandler: MigrationHandler<RocksDBDataStore>? = null
) : AbstractDataStore(dataModelsById) {
    private val columnFamilyHandlesByDataModelIndex = mutableMapOf<UInt, TableColumnFamilies>()
    private val prefixSizesByColumnFamilyHandlesIndex = mutableMapOf<Int, Int>()
    private val uniqueIndicesByDataModelIndex = mutableMapOf<UInt, List<ByteArray>>()

    // Only create Options if no Options were passed. Will take ownership and close it if this object is closed
    private val ownRocksDBOptions: DBOptions? = if (rocksDBOptions == null) DBOptions() else null

    internal val db: RocksDB

    override val storeActor = this.storeActor(this, storeExecutor)

    private val defaultWriteOptions = WriteOptions()
    internal val defaultReadOptions = ReadOptions().apply {
        setPrefixSameAsStart(true)
    }

    private val cache: MutableMap<UInt, MutableMap<Key<*>, MutableMap<IsPropertyReferenceForCache<*, *>, CachedValue>>> = mutableMapOf()

    init {
        val descriptors: MutableList<ColumnFamilyDescriptor> = mutableListOf()
        descriptors.add(ColumnFamilyDescriptor(defaultColumnFamily))
        for ((index, db) in dataModelsById) {
            createColumnFamilyHandles(descriptors, index, db)
        }

        val handles = mutableListOf<ColumnFamilyHandle>()
        this.db = openRocksDB(rocksDBOptions ?: ownRocksDBOptions!!, relativePath, descriptors, handles)

        try {
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

            for ((index, dataModel) in dataModelsById) {
                columnFamilyHandlesByDataModelIndex[index]?.let { tableColumnFamilies ->
                    tableColumnFamilies.model.let { modelColumnFamily ->
                        when (val migrationStatus = checkModelIfMigrationIsNeeded(this.db, modelColumnFamily, dataModel, this.onlyCheckModelVersion)) {
                            UpToDate -> Unit // Do nothing since no work is needed
                            NewModel, OnlySafeAdds -> {
                                // Model updated so can be stored
                                storeModelDefinition(this.db, modelColumnFamily, dataModel)
                            }
                            is NewIndicesOnExistingProperties -> {
                                fillIndex(migrationStatus.indicesToIndex, tableColumnFamilies)
                                storeModelDefinition(this.db, modelColumnFamily, dataModel)
                            }
                            is NeedsMigration -> {
                                val succeeded = migrationHandler?.invoke(this, migrationStatus.storedDataModel as StoredRootDataModel, dataModel)
                                    ?: throw MigrationException("Migration needed: No migration handler present")

                                if (!succeeded) {
                                    throw MigrationException("Migration could not be handled for ${dataModel.name} & ${(migrationStatus.storedDataModel as? StoredRootDataModel)?.version}")
                                }

                                migrationStatus.indicesToIndex?.let {
                                    fillIndex(it, tableColumnFamilies)
                                }

                                // Successful so store new model definition
                                storeModelDefinition(this.db, modelColumnFamily, dataModel)
                            }
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            this.close()
            throw e
        }
    }

    /** Walk all current values in [tableColumnFamilies] and fill [indicesToIndex] */
    private fun fillIndex(
        indicesToIndex: List<IsIndexable>,
        tableColumnFamilies: TableColumnFamilies
    ) {
        for (indexable in indicesToIndex) {
            deleteCompleteIndexContents(this.db, tableColumnFamilies, indexable)
        }

        walkDataRecordsAndFillIndex(this, tableColumnFamilies, indicesToIndex)
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
            val comparatorOptions = ComparatorOptions()
            val comparator = VersionedComparator(comparatorOptions, db.keyByteSize)
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

    internal fun getColumnFamilies(dataModel: IsRootDataModel<*>) =
        columnFamilyHandlesByDataModelIndex[dataModelIdsByString[dataModel.name]]
            ?: throw DefNotFoundException("DataModel definition not found for ${dataModel.name}")

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
            db.put(uniqueHandle, uniqueReference, EMPTY_ARRAY)
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

    /**
     * Read value from the cache if it exists and is of same version, or read it from store and cache it if
     * it is not of same version or does not exist in cache
     */
    internal fun readValueWithCache(
        dbIndex: UInt,
        key: Key<*>,
        reference: IsPropertyReferenceForCache<*, *>,
        version: ULong,
        valueReader: () -> Any?
    ): Any? {
        val refMap = cache.getOrPut(dbIndex) { mutableMapOf() }.getOrPut(key) { mutableMapOf() }
        val value = refMap[reference]

        return if (value != null && version == value.version) {
            value.value
        } else {
            valueReader().also { readValue ->
                if (value == null || value.version < version) {
                    refMap[reference] = CachedValue(version, readValue)
                }
            }
        }
    }

    /** Delete [key] from the table with [dbIndex] */
    fun deleteCacheForKey(dbIndex: UInt, key: Key<*>) {
        cache[dbIndex]?.remove(key)
    }
}
