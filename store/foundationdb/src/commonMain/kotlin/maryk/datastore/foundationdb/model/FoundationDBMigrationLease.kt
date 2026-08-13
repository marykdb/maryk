@file:Suppress("DEPRECATION")

package maryk.datastore.foundationdb.model

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import maryk.foundationdb.TransactionContext
import maryk.foundationdb.Transaction
import maryk.core.models.migration.MigrationLease
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.packKey
import kotlinx.datetime.Clock
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

internal class FoundationDBMigrationLease(
    private val tc: TransactionContext,
    private val modelPrefixesById: Map<UInt, ByteArray>,
    private val scope: CoroutineScope,
    private val leaseTimeoutMs: Long = 30_000L,
    private val heartbeatIntervalMs: Long = 10_000L,
) : MigrationLease {
    private val ownerToken = Random.nextLong().toString()
    private val heartbeatJobs = atomic<Map<UInt, Job>>(emptyMap())
    private val ownerships = atomic<Map<UInt, LeaseOwnership>>(emptyMap())
    private val lossReasons = atomic<Map<UInt, String>>(emptyMap())

    override suspend fun tryAcquire(modelId: UInt, migrationId: String): Boolean {
        val key = modelPrefixesById[modelId]?.let { packKey(it, modelMigrationLeaseKey) } ?: return false

        val acquired = tc.run { tr ->
            val nowMs = Clock.System.now().toEpochMilliseconds()
            val expiresAtMs = nowMs.plusSaturating(leaseTimeoutMs)
            val existing = tr.get(key).awaitResult()?.let(LeaseRecord::fromPersistedBytes)
            if (existing != null && existing.expiresAtMs > nowMs && existing.ownerToken != ownerToken) {
                false
            } else {
                tr.set(key, LeaseRecord(ownerToken, migrationId, expiresAtMs).toPersistedBytes())
                true
            }
        }

        if (acquired) {
            lossReasons.update { it - modelId }
            bindOwner(modelId, migrationId)
            startHeartbeat(modelId, migrationId, key)
        }
        return acquired
    }

    suspend fun bindOwner(modelId: UInt, migrationId: String) {
        val ownerJob = currentCoroutineContext()[Job]
            ?: throw IllegalStateException("Migration lease owner requires a coroutine Job")
        assertPersistedOwnership(modelId, migrationId)
        ownerships.update { it + (modelId to LeaseOwnership(migrationId, ownerJob)) }
    }

    suspend fun assertOwnership(modelId: UInt, migrationId: String) {
        val ownership = ownerships.value[modelId]
        if (ownership?.migrationId != migrationId || ownership.ownerJob != currentCoroutineContext()[Job]) {
            throw leaseLost(modelId, migrationId, "migration job no longer owns the lease")
        }
        assertPersistedOwnership(modelId, migrationId)
    }

    fun leaseLossReason(modelId: UInt, migrationId: String): String? =
        lossReasons.value[modelId]?.takeIf {
            ownerships.value[modelId]?.migrationId == migrationId
        }

    fun requireOwnership(transaction: Transaction, modelId: UInt, migrationId: String) {
        val key = modelPrefixesById[modelId]?.let { packKey(it, modelMigrationLeaseKey) }
            ?: throw leaseLost(modelId, migrationId, "model lease key is unavailable")
        val existing = transaction.get(key).awaitResult()?.let(LeaseRecord::fromPersistedBytes)
        val nowMs = Clock.System.now().toEpochMilliseconds()
        if (
            existing?.ownerToken != ownerToken ||
            existing.migrationId != migrationId ||
            existing.expiresAtMs <= nowMs
        ) {
            val cause = leaseLost(modelId, migrationId, "persisted ownership changed or expired")
            throw cause
        }
    }

    override suspend fun release(modelId: UInt, migrationId: String) {
        heartbeatJobs.value[modelId]?.cancel()
        heartbeatJobs.update { it - modelId }
        ownerships.update { it - modelId }

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
                delay(heartbeatIntervalMs.milliseconds)
                val shouldContinue = try {
                    val nowMs = Clock.System.now().toEpochMilliseconds()
                    val nextExpiry = nowMs.plusSaturating(leaseTimeoutMs)
                    tc.run { tr ->
                        val existing = tr.get(key).awaitResult()?.let(LeaseRecord::fromPersistedBytes)
                        if (
                            existing?.ownerToken == ownerToken &&
                            existing.migrationId == migrationId &&
                            existing.expiresAtMs > nowMs
                        ) {
                            tr.set(key, existing.copy(expiresAtMs = nextExpiry).toPersistedBytes())
                            true
                        } else {
                            false
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    cancelOwner(modelId, migrationId, "heartbeat failed: ${error.message ?: "unknown error"}")
                    return@launch
                }
                if (!shouldContinue) {
                    cancelOwner(modelId, migrationId, "persisted ownership changed or expired")
                    return@launch
                }
            }
        }
        heartbeatJobs.update { it + (modelId to job) }
    }

    private suspend fun assertPersistedOwnership(modelId: UInt, migrationId: String) {
        val key = modelPrefixesById[modelId]?.let { packKey(it, modelMigrationLeaseKey) }
            ?: throw leaseLost(modelId, migrationId, "model lease key is unavailable")
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val ownsLease = try {
            tc.run { tr ->
                val existing = tr.get(key).awaitResult()?.let(LeaseRecord::fromPersistedBytes)
                existing?.ownerToken == ownerToken &&
                    existing.migrationId == migrationId &&
                    existing.expiresAtMs > nowMs
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val cause = leaseLost(modelId, migrationId, "ownership check failed: ${error.message ?: "unknown error"}")
            cancelOwner(modelId, migrationId, cause)
            throw cause
        }
        if (!ownsLease) {
            val cause = leaseLost(modelId, migrationId, "persisted ownership changed or expired")
            cancelOwner(modelId, migrationId, cause)
            throw cause
        }
    }

    private fun cancelOwner(modelId: UInt, migrationId: String, reason: String) {
        cancelOwner(modelId, migrationId, leaseLost(modelId, migrationId, reason))
    }

    private fun cancelOwner(
        modelId: UInt,
        migrationId: String,
        cause: FoundationDBMigrationLeaseLostException,
    ) {
        lossReasons.update { it + (modelId to (cause.message ?: "FoundationDB migration lease lost")) }
        ownerships.value[modelId]
            ?.takeIf { it.migrationId == migrationId }
            ?.ownerJob
            ?.cancel(cause)
    }

    private fun leaseLost(modelId: UInt, migrationId: String, reason: String) =
        FoundationDBMigrationLeaseLostException(
            "FoundationDB migration lease lost for model $modelId migration $migrationId: $reason"
        )

    private data class LeaseOwnership(
        val migrationId: String,
        val ownerJob: Job,
    )

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

internal class FoundationDBMigrationLeaseLostException(message: String) : CancellationException(message)

private fun Long.plusSaturating(value: Long): Long =
    if (this > Long.MAX_VALUE - value) Long.MAX_VALUE else this + value
