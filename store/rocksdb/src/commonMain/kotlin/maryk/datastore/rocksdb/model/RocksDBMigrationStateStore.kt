package maryk.datastore.rocksdb.model

import maryk.core.models.IsRootDataModel
import maryk.core.models.migration.MigrationException
import maryk.core.models.migration.MigrationState
import maryk.core.models.migration.MigrationStateStore
import maryk.rocksdb.ColumnFamilyHandle
import maryk.rocksdb.RocksDB
import maryk.rocksdb.WriteBatch
import maryk.rocksdb.WriteOptions
import maryk.datastore.shared.migration.migrationTargetIdentity
import maryk.datastore.shared.migration.requireMigrationTargetIdentity

internal class RocksDBMigrationStateStore(
    private val rocksDB: RocksDB,
    private val modelColumnFamiliesById: Map<UInt, ColumnFamilyHandle>,
    private val dataModelsById: Map<UInt, IsRootDataModel>,
) : MigrationStateStore {
    suspend fun readProgress(modelId: UInt): Pair<MigrationState, ByteArray?>? {
        val family = modelColumnFamiliesById[modelId] ?: return null
        val state = rocksDB.get(family, modelMigrationStateKey)?.let(MigrationState::requireFromPersistedBytes) ?: return null
        return state to rocksDB.get(family, modelMigrationTargetKey)
    }

    override suspend fun read(modelId: UInt): MigrationState? = readProgress(modelId)?.let { (state, identity) ->
        requireMigrationTargetIdentity(identity, dataModelsById.getValue(modelId))
        state
    }

    override suspend fun write(modelId: UInt, state: MigrationState) {
        val family = modelColumnFamiliesById[modelId] ?: return
        val identity = migrationTargetIdentity(dataModelsById.getValue(modelId))
        rocksDB.get(family, modelMigrationTargetKey)?.let { previous ->
            if (!previous.contentEquals(identity)) throw MigrationException("Migration target schema changed while running for model $modelId")
        }
        WriteBatch().use { batch ->
            batch.put(family, modelMigrationStateKey, state.toPersistedBytes())
            batch.put(family, modelMigrationTargetKey, identity)
            WriteOptions().use { rocksDB.write(it, batch) }
        }
    }

    override suspend fun clear(modelId: UInt) {
        val family = modelColumnFamiliesById[modelId] ?: return
        WriteBatch().use { batch ->
            batch.delete(family, modelMigrationStateKey)
            batch.delete(family, modelMigrationTargetKey)
            WriteOptions().use { rocksDB.write(it, batch) }
        }
    }
}
