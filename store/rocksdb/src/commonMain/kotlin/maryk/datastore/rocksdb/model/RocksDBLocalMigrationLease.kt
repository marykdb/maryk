package maryk.datastore.rocksdb.model

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import maryk.core.models.migration.MigrationLease
import kotlin.random.Random

internal class RocksDBLocalMigrationLease(
    private val storePath: String,
) : MigrationLease {
    private val ownerToken = Random.nextLong().toString()

    override suspend fun tryAcquire(modelId: UInt, migrationId: String): Boolean {
        var acquired = false
        leases.update { current ->
            val key = "$storePath:$modelId"
            val existing = current[key]
            if (existing == null || existing.ownerToken == ownerToken || existing.migrationId == migrationId) {
                acquired = true
                current + (key to LocalLease(ownerToken, migrationId))
            } else {
                current
            }
        }
        return acquired
    }

    override suspend fun release(modelId: UInt, migrationId: String) {
        leases.update { current ->
            val key = "$storePath:$modelId"
            val existing = current[key] ?: return@update current
            if (existing.ownerToken == ownerToken && existing.migrationId == migrationId) {
                current - key
            } else {
                current
            }
        }
    }

    private data class LocalLease(
        val ownerToken: String,
        val migrationId: String,
    )

    private companion object {
        val leases = atomic<Map<String, LocalLease>>(emptyMap())
    }
}
