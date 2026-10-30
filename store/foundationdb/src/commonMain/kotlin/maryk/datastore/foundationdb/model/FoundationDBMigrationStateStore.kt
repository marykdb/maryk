package maryk.datastore.foundationdb.model

import maryk.foundationdb.TransactionContext
import maryk.core.models.IsRootDataModel
import maryk.core.models.migration.MigrationException
import maryk.core.models.migration.MigrationState
import maryk.core.models.migration.MigrationStateStore
import maryk.core.models.migration.MigrationPhase
import maryk.core.models.migration.MigrationStateStatus
import maryk.core.models.migration.MigrationAuditEvent
import maryk.core.properties.types.Version
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.foundationdb.Transaction
import maryk.datastore.shared.migration.migrationTargetIdentity
import maryk.datastore.shared.migration.requireMigrationTargetIdentity

internal class FoundationDBMigrationStateStore(
    private val tc: TransactionContext,
    private val modelPrefixesById: Map<UInt, ByteArray>,
    private val dataModelsById: Map<UInt, IsRootDataModel> = emptyMap(),
) : MigrationStateStore {
    suspend fun readProgress(modelId: UInt): Pair<MigrationState, ByteArray?>? {
        val modelPrefix = modelPrefixesById[modelId] ?: return null
        return tc.run { tr ->
            val bytes = tr.get(packKey(modelPrefix, modelMigrationStateKey)).awaitResult() ?: return@run null
            MigrationState.requireFromPersistedBytes(bytes) to tr.get(packKey(modelPrefix, modelMigrationTargetKey)).awaitResult()
        }
    }

    override suspend fun read(modelId: UInt): MigrationState? = readProgress(modelId)?.let { (state, identity) ->
        dataModelsById[modelId]?.let { requireMigrationTargetIdentity(identity, it) }
        state
    }

    override suspend fun write(modelId: UInt, state: MigrationState) {
        write(modelId, state, null)
    }

    suspend fun write(modelId: UInt, state: MigrationState, guard: ((Transaction) -> Unit)?) {
        val modelPrefix = modelPrefixesById[modelId] ?: return
        val key = packKey(modelPrefix, modelMigrationStateKey)
        val identity = dataModelsById[modelId]?.let { migrationTargetIdentity(it) }
        tc.run { tr ->
            guard?.invoke(tr)
            tr.set(key, state.toPersistedBytes())
            identity?.let { storeTargetIdentity(tr, modelPrefix, it) }
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
        val identity = dataModelsById[modelId]?.let { migrationTargetIdentity(it) }
        tc.run { transaction ->
            guard?.invoke(transaction)
            transaction.set(key, state.toPersistedBytes())
            identity?.let { storeTargetIdentity(transaction, modelPrefix, it) }
            auditStore.append(transaction, modelId, event)
        }
    }

    private fun storeTargetIdentity(transaction: Transaction, modelPrefix: ByteArray, identity: ByteArray) {
        val key = packKey(modelPrefix, modelMigrationTargetKey)
        transaction.get(key).awaitResult()?.let { previous ->
            if (!previous.contentEquals(identity)) throw MigrationException("Migration target schema changed while running")
        }
        transaction.set(key, identity)
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

    suspend fun clearFinalizedForPublishedVersion(
        modelId: UInt,
        publishedVersion: Version,
        expectedState: MigrationState? = null,
        expectedIdentity: ByteArray? = null,
    ) {
        val modelPrefix = modelPrefixesById[modelId] ?: return
        val stateKey = packKey(modelPrefix, modelMigrationStateKey)
        val versionKey = packKey(modelPrefix, modelVersionKey)
        val expectedVersion = publishedVersion.toByteArray()
        tc.run { transaction ->
            val stateBytes = transaction.get(stateKey).awaitResult() ?: return@run
            val storedVersion = transaction.get(versionKey).awaitResult() ?: return@run
            if (!storedVersion.contentEquals(expectedVersion)) return@run
            val state = MigrationState.requireFromPersistedBytes(stateBytes)
            if (expectedState != null && (state != expectedState ||
                    !transaction.get(packKey(modelPrefix, modelMigrationTargetKey)).awaitResult().contentEquals(expectedIdentity))) return@run
            if (
                state.toVersion == publishedVersion.toString() &&
                state.phase == MigrationPhase.Contract &&
                state.status == MigrationStateStatus.Running &&
                state.message == MIGRATION_FINALIZATION_PENDING_MESSAGE
            ) {
                clear(transaction, modelId)
            }
        }
    }

    fun clear(transaction: Transaction, modelId: UInt) {
        val modelPrefix = modelPrefixesById[modelId] ?: return
        transaction.clear(packKey(modelPrefix, modelMigrationStateKey))
        transaction.clear(packKey(modelPrefix, modelMigrationTargetKey))
    }
}

internal const val MIGRATION_FINALIZATION_PENDING_MESSAGE = "Migration phases complete; finalization pending"
