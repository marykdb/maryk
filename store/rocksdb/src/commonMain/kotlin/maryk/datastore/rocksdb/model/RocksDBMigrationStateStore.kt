package maryk.datastore.rocksdb.model

import maryk.core.models.migration.MigrationState
import maryk.core.models.migration.MigrationStateStore
import maryk.rocksdb.ColumnFamilyHandle
import maryk.rocksdb.RocksDB

internal class RocksDBMigrationStateStore(
    private val rocksDB: RocksDB,
    private val modelColumnFamiliesById: Map<UInt, ColumnFamilyHandle>,
) : MigrationStateStore {
    override suspend fun read(modelId: UInt): MigrationState? =
        modelColumnFamiliesById[modelId]
            ?.let { rocksDB.get(it, modelMigrationStateKey) }
            ?.let(MigrationState::fromPersistedBytes)

    override suspend fun write(modelId: UInt, state: MigrationState) {
        modelColumnFamiliesById[modelId]?.let {
            rocksDB.put(it, modelMigrationStateKey, state.toPersistedBytes())
        }
    }

    override suspend fun clear(modelId: UInt) {
        modelColumnFamiliesById[modelId]?.let {
            rocksDB.delete(it, modelMigrationStateKey)
        }
    }
}
