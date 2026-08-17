package maryk.datastore.foundationdb.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import maryk.foundationdb.Transaction
import maryk.foundationdb.TransactionContext
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.runBoundedIntegrationTest
import maryk.datastore.foundationdb.FoundationDBMigrationLeaseConfiguration
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

class FoundationDBMigrationLeaseTest {
    @Test
    fun leaseConfigurationRejectsInvalidTiming() {
        assertFailsWith<IllegalArgumentException> {
            FoundationDBMigrationLeaseConfiguration(migrationLeaseTimeoutMs = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            FoundationDBMigrationLeaseConfiguration(migrationLeaseHeartbeatMs = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            FoundationDBMigrationLeaseConfiguration(
                migrationLeaseTimeoutMs = 100,
                migrationLeaseHeartbeatMs = 100,
            )
        }
    }

    @Test
    fun heartbeatRenewsLeaseAndPreventsTakeover() = runBoundedIntegrationTest {
        val dirPath = listOf("maryk", "test", "fdb-lease-heartbeat", Uuid.random().toString())
        val dataStore = FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to SimpleMarykModel)
        )

        val modelPrefix = dataStore.getTableDirs(1u).modelPrefix
        val ownerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val contenderScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val ownerLease = FoundationDBMigrationLease(
            tc = dataStore.tc,
            modelPrefixesById = mapOf(1u to modelPrefix),
            scope = ownerScope,
            leaseTimeoutMs = 1_500,
            heartbeatIntervalMs = 100,
        )
        val contenderLease = FoundationDBMigrationLease(
            tc = dataStore.tc,
            modelPrefixesById = mapOf(1u to modelPrefix),
            scope = contenderScope,
            leaseTimeoutMs = 1_500,
            heartbeatIntervalMs = 100,
        )

        try {
            assertTrue(ownerLease.tryAcquire(1u, "migration-v1-v2"))
            repeat(5) {
                delay(250.milliseconds)
                assertFalse(contenderLease.tryAcquire(1u, "migration-v1-v2"))
            }
        } finally {
            ownerLease.release(1u, "migration-v1-v2")
            contenderLease.release(1u, "migration-v1-v2")
            ownerScope.cancel()
            contenderScope.cancel()
            dataStore.close()
        }
    }

    @Test
    fun expiredLeaseCanBeTakenOverWithoutHeartbeatRenewal() = runBoundedIntegrationTest {
        val dirPath = listOf("maryk", "test", "fdb-lease-timeout", Uuid.random().toString())
        val dataStore = FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to SimpleMarykModel)
        )

        val modelPrefix = dataStore.getTableDirs(1u).modelPrefix
        val ownerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val contenderScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val ownerLease = FoundationDBMigrationLease(
            tc = dataStore.tc,
            modelPrefixesById = mapOf(1u to modelPrefix),
            scope = ownerScope,
            leaseTimeoutMs = 400,
            heartbeatIntervalMs = 5_000,
        )
        val contenderLease = FoundationDBMigrationLease(
            tc = dataStore.tc,
            modelPrefixesById = mapOf(1u to modelPrefix),
            scope = contenderScope,
            leaseTimeoutMs = 400,
            heartbeatIntervalMs = 5_000,
        )

        try {
            assertTrue(ownerLease.tryAcquire(1u, "migration-v1-v2"))
            delay(900.milliseconds)
            assertTrue(contenderLease.tryAcquire(1u, "migration-v1-v2"))
        } finally {
            ownerLease.release(1u, "migration-v1-v2")
            contenderLease.release(1u, "migration-v1-v2")
            ownerScope.cancel()
            contenderScope.cancel()
            dataStore.close()
        }
    }

    @Test
    fun retriedAcquireUsesTheRetryAttemptTime() = runBoundedIntegrationTest {
        val dirPath = listOf("maryk", "test", "fdb-lease-retry", Uuid.random().toString())
        val dataStore = FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to SimpleMarykModel)
        )

        val modelPrefix = dataStore.getTableDirs(1u).modelPrefix
        val key = packKey(modelPrefix, modelMigrationLeaseKey)
        val ownerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val contenderScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val ownerLease = FoundationDBMigrationLease(
            tc = ConflictOnFirstAttemptTransactionContext(dataStore.tc, key),
            modelPrefixesById = mapOf(1u to modelPrefix),
            scope = ownerScope,
            leaseTimeoutMs = 100,
            heartbeatIntervalMs = 5_000,
        )
        val contenderLease = FoundationDBMigrationLease(
            tc = dataStore.tc,
            modelPrefixesById = mapOf(1u to modelPrefix),
            scope = contenderScope,
            leaseTimeoutMs = 100,
            heartbeatIntervalMs = 5_000,
        )

        try {
            assertTrue(ownerLease.tryAcquire(1u, "migration-v1-v2"))
            assertFalse(contenderLease.tryAcquire(1u, "migration-v1-v2"))
        } finally {
            ownerLease.release(1u, "migration-v1-v2")
            contenderLease.release(1u, "migration-v1-v2")
            ownerScope.cancel()
            contenderScope.cancel()
            dataStore.close()
        }
    }

    @Test
    fun transactionalGuardRejectsSynchronousWriteAfterOwnershipReplacement() = runBoundedIntegrationTest {
        val dirPath = listOf("maryk", "test", "fdb-lease-fenced-write", Uuid.random().toString())
        val dataStore = FoundationDBDataStore.open(
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to SimpleMarykModel),
        )
        val modelPrefix = dataStore.getTableDirs(1u).modelPrefix
        val leaseKey = packKey(modelPrefix, modelMigrationLeaseKey)
        val protectedKey = packKey(modelPrefix, byteArrayOf(99))
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val lease = FoundationDBMigrationLease(dataStore.tc, mapOf(1u to modelPrefix), scope)
        try {
            assertTrue(lease.tryAcquire(1u, "migration-v1-v2"))
            dataStore.tc.run { transaction ->
                transaction.set(
                    leaseKey,
                    "v=1\nowner=contender\nmigration=migration-v1-v2\nexpires=${Long.MAX_VALUE}\n".encodeToByteArray(),
                )
            }
            assertFailsWith<FoundationDBMigrationLeaseLostException> {
                dataStore.tc.run { transaction ->
                    lease.requireOwnership(transaction, 1u, "migration-v1-v2")
                    transaction.set(protectedKey, byteArrayOf(1))
                }
            }
            assertNull(dataStore.tc.run { transaction -> transaction.get(protectedKey).awaitResult() })
        } finally {
            scope.cancel()
            dataStore.close()
        }
    }

    private class ConflictOnFirstAttemptTransactionContext(
        private val delegate: TransactionContext,
        private val conflictKey: ByteArray,
    ) : TransactionContext by delegate {
        private var attempts = 0

        override fun <T> run(block: (Transaction) -> T): T = delegate.run { transaction ->
            block(transaction).also {
                if (attempts++ == 0) {
                    delegate.run { conflictTransaction ->
                        conflictTransaction.set(conflictKey, byteArrayOf(0))
                    }
                    runBlocking { delay(300.milliseconds) }
                }
            }
        }
    }
}
