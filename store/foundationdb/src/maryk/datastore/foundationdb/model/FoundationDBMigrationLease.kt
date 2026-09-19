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
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

internal class FoundationDBMigrationLease(
    private val tc: TransactionContext,
    private val modelPrefixesById: Map<UInt, ByteArray>,
    private val scope: CoroutineScope,
    private val leaseTimeoutMs: Long = 30_000L,
    private val heartbeatIntervalMs: Long = 10_000L,
    private val isStoreClosing: () -> Boolean = { false },
) : MigrationLease {
    private val ownerToken = Random.nextLong().toString()
    private val heartbeatJobs = atomic<Map<UInt, Job>>(emptyMap())
    private val ownerships = atomic<Map<UInt, LeaseOwnership>>(emptyMap())
    private val fencingTokens = atomic<Map<UInt, ULong>>(emptyMap())
    private val lossReasons = atomic<Map<UInt, String>>(emptyMap())

    override suspend fun tryAcquire(modelId: UInt, migrationId: String): Boolean {
        val key = modelPrefixesById[modelId]?.let { packKey(it, modelMigrationLeaseKey) } ?: return false

        val acquired = tc.run { tr ->
            val nowMs = Clock.System.now().toEpochMilliseconds()
            val expiresAtMs = nowMs.plusSaturating(leaseTimeoutMs)
            val existing = tr.get(key).awaitResult()?.let(LeaseRecord::fromPersistedBytes)
            if (existing != null && existing.expiresAtMs > nowMs && existing.ownerToken != ownerToken) {
                null
            } else {
                val fencingToken = existing?.fencingToken.nextFencingToken(modelId, migrationId)
                LeaseRecord(ownerToken, migrationId, expiresAtMs, fencingToken).also { record ->
                    tr.set(key, record.toPersistedBytes())
                }
            }
        }

        if (acquired != null) {
            lossReasons.update { it - modelId }
            fencingTokens.update { it + (modelId to acquired.fencingToken) }
            bindOwner(modelId, migrationId)
            startHeartbeat(modelId, migrationId, key)
        }
        return acquired != null
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
        val expectedFencingToken = fencingTokens.value[modelId]
        val nowMs = Clock.System.now().toEpochMilliseconds()
        if (
            existing?.ownerToken != ownerToken ||
            existing.migrationId != migrationId ||
            existing.fencingToken != expectedFencingToken ||
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
        val expectedFencingToken = fencingTokens.value[modelId]
        fencingTokens.update { it - modelId }

        val key = modelPrefixesById[modelId]?.let { packKey(it, modelMigrationLeaseKey) } ?: return
        try {
            tc.run { tr ->
                val existing = tr.get(key).awaitResult()?.let(LeaseRecord::fromPersistedBytes)
                if (
                    existing?.ownerToken == ownerToken &&
                    existing.migrationId == migrationId &&
                    existing.fencingToken == expectedFencingToken
                ) {
                    // Retain the last fencing token so a later acquisition is strictly monotonic.
                    tr.set(key, existing.copy(expiresAtMs = 0L).toPersistedBytes())
                }
            }
        } catch (error: IllegalStateException) {
            // Native close may be required to abort a blocking FDB future before this canceled
            // background job reaches finally. The live lease then expires normally.
            if (!isStoreClosing()) throw error
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
                            existing.fencingToken == fencingTokens.value[modelId] &&
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
                    if (isStoreClosing()) return@launch
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
                    existing.fencingToken == fencingTokens.value[modelId] &&
                    existing.expiresAtMs > nowMs
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (isStoreClosing()) throw CancellationException("Datastore closing")
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
        val fencingToken: ULong,
    ) {
        fun toPersistedBytes(): ByteArray = buildString {
            // Keep the established header version so rolling-upgrade readers retain ownership
            // semantics. Older readers ignore the additional fencing field.
            append("v=1\n")
            append("owner=").append(ownerToken).append('\n')
            append("migration=").append(migrationId).append('\n')
            append("expires=").append(expiresAtMs).append('\n')
            append("fence=").append(fencingToken).append('\n')
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

                val version = entries["v"]
                if (version != "1" && version != "2") return null
                val owner = entries["owner"] ?: return null
                val migration = entries["migration"] ?: return null
                val expires = entries["expires"]?.toLongOrNull() ?: return null
                val fencingToken = when (version) {
                    "1" -> entries["fence"]?.toULongOrNull() ?: 0uL
                    else -> entries["fence"]?.toULongOrNull() ?: return null
                }
                return LeaseRecord(owner, migration, expires, fencingToken)
            }
        }
    }
}

private fun ULong?.nextFencingToken(modelId: UInt, migrationId: String): ULong = when (this) {
    null -> 1uL
    ULong.MAX_VALUE -> throw IllegalStateException(
        "FoundationDB migration fencing token exhausted for model $modelId migration $migrationId"
    )
    else -> this + 1uL
}

internal class FoundationDBMigrationLeaseLostException(message: String) : CancellationException(message)

private fun Long.plusSaturating(value: Long): Long =
    if (this > Long.MAX_VALUE - value) Long.MAX_VALUE else this + value
