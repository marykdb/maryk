package maryk.datastore.rocksdb.model

import maryk.core.models.migration.MigrationAuditEvent
import maryk.core.models.migration.MigrationAuditLogStore
import maryk.rocksdb.ColumnFamilyHandle
import maryk.rocksdb.RocksDB

internal class RocksDBMigrationAuditLogStore(
    private val rocksDB: RocksDB,
    private val modelColumnFamiliesById: Map<UInt, ColumnFamilyHandle>,
    private val maxEntries: Int = 1000,
) : MigrationAuditLogStore {
    override suspend fun append(modelId: UInt, event: MigrationAuditEvent) {
        val handle = modelColumnFamiliesById[modelId] ?: return
        val current = rocksDB.get(handle, modelMigrationAuditLogKey)
            ?.decodeToString()
            ?.lineSequence()
            ?.filter { it.isNotBlank() }
            ?.toMutableList()
            ?: mutableListOf()
        current.add(event.toPersistedLine())
        if (current.size > maxEntries) {
            val removeCount = current.size - maxEntries
            repeat(removeCount) { current.removeAt(0) }
        }
        rocksDB.put(handle, modelMigrationAuditLogKey, current.joinToString("\n").encodeToByteArray())
    }

    override suspend fun read(modelId: UInt, limit: Int): List<MigrationAuditEvent> {
        val handle = modelColumnFamiliesById[modelId] ?: return emptyList()
        return rocksDB.get(handle, modelMigrationAuditLogKey)
            ?.decodeToString()
            ?.lineSequence()
            ?.filter { it.isNotBlank() }
            ?.mapNotNull(MigrationAuditEvent::fromPersistedLine)
            ?.toList()
            ?.takeLast(limit.coerceAtLeast(0))
            ?: emptyList()
    }
}
