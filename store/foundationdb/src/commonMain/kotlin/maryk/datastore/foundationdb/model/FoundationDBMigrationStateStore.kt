package maryk.datastore.foundationdb.model

import maryk.foundationdb.TransactionContext
import maryk.core.models.migration.MigrationState
import maryk.core.models.migration.MigrationStateStore
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.packKey

internal class FoundationDBMigrationStateStore(
    private val tc: TransactionContext,
    private val modelPrefixesById: Map<UInt, ByteArray>,
) : MigrationStateStore {
    override suspend fun read(modelId: UInt): MigrationState? {
        val modelPrefix = modelPrefixesById[modelId] ?: return null
        val key = packKey(modelPrefix, modelMigrationStateKey)
        val bytes = tc.run { tr ->
            tr.get(key).awaitResult()
        } ?: return null
        return MigrationState.fromPersistedBytes(bytes)
    }

    override suspend fun write(modelId: UInt, state: MigrationState) {
        val modelPrefix = modelPrefixesById[modelId] ?: return
        val key = packKey(modelPrefix, modelMigrationStateKey)
        tc.run { tr ->
            tr.set(key, state.toPersistedBytes())
        }
    }

    override suspend fun clear(modelId: UInt) {
        val modelPrefix = modelPrefixesById[modelId] ?: return
        val key = packKey(modelPrefix, modelMigrationStateKey)
        tc.run { tr ->
            tr.clear(key)
        }
    }
}
