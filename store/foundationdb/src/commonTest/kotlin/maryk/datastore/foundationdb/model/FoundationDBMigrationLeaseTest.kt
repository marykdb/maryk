@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package maryk.datastore.foundationdb.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class FoundationDBMigrationLeaseTest {
    @Test
    fun heartbeatRenewsLeaseAndPreventsTakeover() = runBlocking {
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
            leaseTimeoutMs = 200,
            heartbeatIntervalMs = 40,
        )
        val contenderLease = FoundationDBMigrationLease(
            tc = dataStore.tc,
            modelPrefixesById = mapOf(1u to modelPrefix),
            scope = contenderScope,
            leaseTimeoutMs = 200,
            heartbeatIntervalMs = 40,
        )

        try {
            assertTrue(ownerLease.tryAcquire(1u, "migration-v1-v2"))
            delay(650)
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
    fun expiredLeaseCanBeTakenOverWithoutHeartbeatRenewal() = runBlocking {
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
            leaseTimeoutMs = 150,
            heartbeatIntervalMs = 5_000,
        )
        val contenderLease = FoundationDBMigrationLease(
            tc = dataStore.tc,
            modelPrefixesById = mapOf(1u to modelPrefix),
            scope = contenderScope,
            leaseTimeoutMs = 150,
            heartbeatIntervalMs = 5_000,
        )

        try {
            assertTrue(ownerLease.tryAcquire(1u, "migration-v1-v2"))
            delay(300)
            assertTrue(contenderLease.tryAcquire(1u, "migration-v1-v2"))
        } finally {
            ownerLease.release(1u, "migration-v1-v2")
            contenderLease.release(1u, "migration-v1-v2")
            ownerScope.cancel()
            contenderScope.cancel()
            dataStore.close()
        }
    }
}
