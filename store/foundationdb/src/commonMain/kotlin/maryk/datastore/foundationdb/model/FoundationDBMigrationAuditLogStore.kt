package maryk.datastore.foundationdb.model

import maryk.core.models.migration.MigrationAuditEvent
import maryk.core.models.migration.MigrationAuditLogStore
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.foundationdb.TransactionContext

internal class FoundationDBMigrationAuditLogStore(
    private val tc: TransactionContext,
    private val modelPrefixesById: Map<UInt, ByteArray>,
    private val maxEntries: Int = 1000,
) : MigrationAuditLogStore {
    init {
        require(maxEntries > 0) { "maxEntries should be positive but was $maxEntries" }
    }

    override suspend fun append(modelId: UInt, event: MigrationAuditEvent) {
        val modelPrefix = modelPrefixesById[modelId] ?: return
        val key = packKey(modelPrefix, modelMigrationAuditLogKey)
        tc.run { tr ->
            val current = tr.get(key).awaitResult()
                ?.decodeToString()
                ?.lineSequence()
                ?.filter { it.isNotBlank() }
                ?.toMutableList()
                ?: mutableListOf()
            if (current.size >= maxEntries) {
                current.subList(0, current.size - maxEntries + 1).clear()
            }
            current.add(event.toPersistedLine())
            tr.set(key, current.joinToString("\n").encodeToByteArray())
        }
    }

    override suspend fun read(modelId: UInt, limit: Int): List<MigrationAuditEvent> {
        val modelPrefix = modelPrefixesById[modelId] ?: return emptyList()
        val key = packKey(modelPrefix, modelMigrationAuditLogKey)
        return tc.run { tr ->
            tr.get(key).awaitResult()
        }?.decodeToString()
            ?.lineSequence()
            ?.filter { it.isNotBlank() }
            ?.mapNotNull(MigrationAuditEvent::fromPersistedLine)
            ?.toList()
            ?.takeLast(limit.coerceAtLeast(0))
            ?: emptyList()
    }
}
