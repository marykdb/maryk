package maryk.datastore.foundationdb.model

import maryk.foundationdb.TransactionContext
import maryk.core.models.migration.MigrationState
import maryk.core.models.migration.MigrationStateStore
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.foundationdb.Transaction

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
        return MigrationState.requireFromPersistedBytes(bytes)
    }

    override suspend fun write(modelId: UInt, state: MigrationState) {
        write(modelId, state, null)
    }

    suspend fun write(modelId: UInt, state: MigrationState, guard: ((Transaction) -> Unit)?) {
        val modelPrefix = modelPrefixesById[modelId] ?: return
        val key = packKey(modelPrefix, modelMigrationStateKey)
        tc.run { tr ->
            guard?.invoke(tr)
            tr.set(key, state.toPersistedBytes())
        }
    }

    override suspend fun clear(modelId: UInt) {
        clear(modelId, null)
    }

    suspend fun clear(modelId: UInt, guard: ((Transaction) -> Unit)?) {
        val modelPrefix = modelPrefixesById[modelId] ?: return
        val key = packKey(modelPrefix, modelMigrationStateKey)
        tc.run { tr ->
            guard?.invoke(tr)
            tr.clear(key)
        }
    }
}
