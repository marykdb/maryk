package maryk.datastore.foundationdb.model

import maryk.foundationdb.TransactionContext
import maryk.core.models.migration.MigrationState
import maryk.core.models.migration.MigrationStateStore
import maryk.core.models.migration.MigrationPhase
import maryk.core.models.migration.MigrationStateStatus
import maryk.core.models.migration.MigrationAuditEvent
import maryk.core.properties.types.Version
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

    internal suspend fun writeWithAudit(
        modelId: UInt,
        state: MigrationState,
        auditStore: FoundationDBMigrationAuditLogStore,
        event: MigrationAuditEvent,
        guard: ((Transaction) -> Unit)? = null,
    ) {
        val modelPrefix = modelPrefixesById[modelId] ?: return
        val key = packKey(modelPrefix, modelMigrationStateKey)
        tc.run { transaction ->
            guard?.invoke(transaction)
            transaction.set(key, state.toPersistedBytes())
            auditStore.append(transaction, modelId, event)
        }
    }

    override suspend fun clear(modelId: UInt) {
        clear(modelId, null)
    }

    suspend fun clear(modelId: UInt, guard: ((Transaction) -> Unit)?) {
        val modelPrefix = modelPrefixesById[modelId] ?: return
        tc.run { tr ->
            guard?.invoke(tr)
            clear(tr, modelId)
        }
    }

    suspend fun clearFinalizedForPublishedVersion(modelId: UInt, publishedVersion: Version) {
        val modelPrefix = modelPrefixesById[modelId] ?: return
        val stateKey = packKey(modelPrefix, modelMigrationStateKey)
        val versionKey = packKey(modelPrefix, modelVersionKey)
        val expectedVersion = publishedVersion.toByteArray()
        tc.run { transaction ->
            val stateBytes = transaction.get(stateKey).awaitResult() ?: return@run
            val storedVersion = transaction.get(versionKey).awaitResult() ?: return@run
            if (!storedVersion.contentEquals(expectedVersion)) return@run
            val state = MigrationState.requireFromPersistedBytes(stateBytes)
            if (
                state.toVersion == publishedVersion.toString() &&
                state.phase == MigrationPhase.Contract &&
                state.status == MigrationStateStatus.Running &&
                state.message == MIGRATION_FINALIZATION_PENDING_MESSAGE
            ) {
                transaction.clear(stateKey)
            }
        }
    }

    fun clear(transaction: Transaction, modelId: UInt) {
        val modelPrefix = modelPrefixesById[modelId] ?: return
        transaction.clear(packKey(modelPrefix, modelMigrationStateKey))
    }
}

internal const val MIGRATION_FINALIZATION_PENDING_MESSAGE = "Migration phases complete; finalization pending"
