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
    init {
        require(maxEntries > 0) { "maxEntries should be positive but was $maxEntries" }
    }

    override suspend fun append(modelId: UInt, event: MigrationAuditEvent) {
        val handle = modelColumnFamiliesById[modelId] ?: return
        val current = rocksDB.get(handle, modelMigrationAuditLogKey)
            ?.decodeToString()
            ?.lineSequence()
            ?.filter { it.isNotBlank() }
            ?.toMutableList()
            ?: mutableListOf()
        if (current.size >= maxEntries) {
            current.subList(0, current.size - maxEntries + 1).clear()
        }
        current.add(event.toPersistedLine())
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
