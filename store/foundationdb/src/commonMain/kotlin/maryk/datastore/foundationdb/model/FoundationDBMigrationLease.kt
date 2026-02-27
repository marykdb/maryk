@file:Suppress("DEPRECATION")

package maryk.datastore.foundationdb.model

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import maryk.foundationdb.TransactionContext
import maryk.core.models.migration.MigrationLease
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.packKey
import kotlinx.datetime.Clock
import kotlin.random.Random

internal class FoundationDBMigrationLease(
    private val tc: TransactionContext,
    private val modelPrefixesById: Map<UInt, ByteArray>,
    private val scope: CoroutineScope,
    private val leaseTimeoutMs: Long = 30_000L,
    private val heartbeatIntervalMs: Long = 10_000L,
) : MigrationLease {
    private val ownerToken = Random.nextLong().toString()
    private val heartbeatJobs = atomic<Map<UInt, Job>>(emptyMap())

    override suspend fun tryAcquire(modelId: UInt, migrationId: String): Boolean {
        val key = modelPrefixesById[modelId]?.let { packKey(it, modelMigrationLeaseKey) } ?: return false
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val expiresAtMs = nowMs + leaseTimeoutMs

        val acquired = tc.run { tr ->
            val existing = tr.get(key).awaitResult()?.let(LeaseRecord::fromPersistedBytes)
            if (existing != null && existing.expiresAtMs > nowMs && existing.ownerToken != ownerToken) {
                false
            } else {
                tr.set(key, LeaseRecord(ownerToken, migrationId, expiresAtMs).toPersistedBytes())
                true
            }
        }

        if (acquired) {
            startHeartbeat(modelId, migrationId, key)
        }
        return acquired
    }

    override suspend fun release(modelId: UInt, migrationId: String) {
        heartbeatJobs.value[modelId]?.cancel()
        heartbeatJobs.update { it - modelId }

        val key = modelPrefixesById[modelId]?.let { packKey(it, modelMigrationLeaseKey) } ?: return
        tc.run { tr ->
            val existing = tr.get(key).awaitResult()?.let(LeaseRecord::fromPersistedBytes)
            if (existing?.ownerToken == ownerToken && existing.migrationId == migrationId) {
                tr.clear(key)
            }
        }
    }

    private fun startHeartbeat(modelId: UInt, migrationId: String, key: ByteArray) {
        heartbeatJobs.value[modelId]?.cancel()
        val job = scope.launch {
            while (true) {
                delay(heartbeatIntervalMs)
                val nowMs = Clock.System.now().toEpochMilliseconds()
                val nextExpiry = nowMs + leaseTimeoutMs
                val shouldContinue = tc.run { tr ->
                    val existing = tr.get(key).awaitResult()?.let(LeaseRecord::fromPersistedBytes)
                    if (existing?.ownerToken == ownerToken && existing.migrationId == migrationId) {
                        tr.set(key, existing.copy(expiresAtMs = nextExpiry).toPersistedBytes())
                        true
                    } else {
                        false
                    }
                }
                if (!shouldContinue) {
                    break
                }
            }
        }
        heartbeatJobs.update { it + (modelId to job) }
    }

    private data class LeaseRecord(
        val ownerToken: String,
        val migrationId: String,
        val expiresAtMs: Long,
    ) {
        fun toPersistedBytes(): ByteArray = buildString {
            append("v=1\n")
            append("owner=").append(ownerToken).append('\n')
            append("migration=").append(migrationId).append('\n')
            append("expires=").append(expiresAtMs).append('\n')
        }.encodeToByteArray()

        companion object {
            fun fromPersistedBytes(bytes: ByteArray): LeaseRecord? {
                val entries = bytes.decodeToString()
                    .lineSequence()
                    .mapNotNull { line ->
                        val split = line.indexOf('=')
                        if (split <= 0) null else line.substring(0, split) to line.substring(split + 1)
                    }
                    .toMap()

                if (entries["v"] != "1") return null
                val owner = entries["owner"] ?: return null
                val migration = entries["migration"] ?: return null
                val expires = entries["expires"]?.toLongOrNull() ?: return null
                return LeaseRecord(owner, migration, expires)
            }
        }
    }
}
