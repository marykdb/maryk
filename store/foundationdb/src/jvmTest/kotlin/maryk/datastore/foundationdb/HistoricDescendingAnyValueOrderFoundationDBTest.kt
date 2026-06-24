package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.core.query.orders.descending
import maryk.core.query.requests.add
import maryk.core.query.requests.scan
import maryk.core.query.responses.FetchByIndexScan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.test.models.AnyValueIncMapIndexModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

class HistoricDescendingAnyValueOrderFoundationDBTest {
    @Test
    fun currentAndHistoricDescendingAnyValueScanMatchOrder() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "historic-desc-any-value-order", Uuid.random().toString()),
            dataModelsById = mapOf(1u to AnyValueIncMapIndexModel),
            keepAllVersions = true,
        )

        try {
            val statuses = store.execute(
                AnyValueIncMapIndexModel.add(
                    AnyValueIncMapIndexModel.create {
                        name with "a"
                        incMapValues with mapOf(
                            1u to "i1",
                            4u to "i4"
                        )
                    },
                    AnyValueIncMapIndexModel.create {
                        name with "b"
                        incMapValues with mapOf(3u to "i3")
                    },
                    AnyValueIncMapIndexModel.create {
                        name with "c"
                        incMapValues with mapOf(2u to "i2")
                    },
                )
            ).statuses.map { assertIs<AddSuccess<AnyValueIncMapIndexModel>>(it) }

            val currentScan = store.execute(
                AnyValueIncMapIndexModel.scan(
                    order = AnyValueIncMapIndexModel { incMapValues.refToAnyKey() }.descending()
                )
            )
            val historicScan = store.execute(
                AnyValueIncMapIndexModel.scan(
                    toVersion = statuses.maxOf { it.version },
                    order = AnyValueIncMapIndexModel { incMapValues.refToAnyKey() }.descending()
                )
            )

            assertIs<FetchByIndexScan>(currentScan.dataFetchType)
            assertIs<FetchByIndexScan>(historicScan.dataFetchType)
            assertEquals(
                currentScan.values.map { it.values { name } }.distinct(),
                historicScan.values.map { it.values { name } }.distinct()
            )
        } finally {
            store.close()
        }
    }
}
