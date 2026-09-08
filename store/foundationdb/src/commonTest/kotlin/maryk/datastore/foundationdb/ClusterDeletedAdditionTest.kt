package maryk.datastore.foundationdb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import maryk.core.clock.HLC
import maryk.core.properties.types.Key
import maryk.core.query.requests.scan
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.InitialValuesUpdate
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

class ClusterDeletedAdditionTest {
    @Test
    fun replicatedDeletedAdditionKeepsDeletionFlagOnOtherNode() = runTest(timeout = 3.minutes) {
        withContext(Dispatchers.Default) {
            val root = listOf("maryk", "test", "cluster-deleted-addition", Uuid.random().toString())
            val incompatibleStore = FoundationDBDataStore.open(
                directoryPath = root + "incompatible",
                dataModelsById = mapOf(1u to SimpleMarykModel),
                clusterUpdateLogConfiguration = FoundationDBClusterUpdateLogConfiguration(
                    enableClusterUpdateLog = true, clusterUpdateLogConsumerId = "incompatible",
                ),
            )
            try {
                val version = HLC().timestamp
                val key = Key<SimpleMarykModel>(ByteArray(16) { 15 })
                assertFailsWith<IllegalArgumentException> {
                    incompatibleStore.processUpdate(UpdateResponse(SimpleMarykModel, AdditionUpdate(
                        key, version, version, 0, true, SimpleMarykModel.create { value with "deleted" },
                    )))
                }
            } finally {
                incompatibleStore.close()
            }
            val first = FoundationDBDataStore.open(
                directoryPath = root, dataModelsById = mapOf(1u to SimpleMarykModel),
                clusterUpdateLogConfiguration = FoundationDBClusterUpdateLogConfiguration(
                    enableClusterUpdateLog = true, clusterUpdateLogConsumerId = "writer", clusterUpdateLogAllowDeletedAdditions = true,
                ),
            )
            try {
                val second = FoundationDBDataStore.open(
                    directoryPath = root, dataModelsById = mapOf(1u to SimpleMarykModel),
                    clusterUpdateLogConfiguration = FoundationDBClusterUpdateLogConfiguration(
                        enableClusterUpdateLog = true, clusterUpdateLogConsumerId = "reader", clusterUpdateLogAllowDeletedAdditions = true,
                    ),
                )
                try {
                    val updates = second.executeFlow(SimpleMarykModel.scan(filterSoftDeleted = false, allowTableScan = true)).produceIn(this)
                    try {
                        assertIs<InitialValuesUpdate<*>>(withTimeout(10_000) { updates.receive() })
                        val version = HLC().timestamp
                        val key = Key<SimpleMarykModel>(ByteArray(16) { 14 })
                        first.processUpdate(UpdateResponse(SimpleMarykModel, AdditionUpdate(
                            key, version, version, 0, true, SimpleMarykModel.create { value with "happy deleted" },
                        )))
                        val addition = assertIs<AdditionUpdate<SimpleMarykModel>>(withTimeout(10_000) { updates.receive() })
                        assertEquals(key, addition.key)
                        assertTrue(addition.isDeleted, "Cluster log must preserve the replicated deletion state")
                    } finally {
                        updates.cancel()
                    }
                } finally {
                    second.close()
                }
            } finally {
                first.close()
            }
        }
    }
}
