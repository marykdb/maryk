package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.core.models.key
import maryk.core.properties.enum.invoke
import maryk.core.properties.types.invoke
import maryk.core.query.requests.add
import maryk.core.query.requests.scanUpdates
import maryk.core.query.responses.FetchByUpdateHistoryIndex
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.createTestDBFolder
import maryk.datastore.test.dataModelsForTests
import maryk.deleteFolder
import maryk.test.models.ComplexModel
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.MarykTypeEnum.T1
import maryk.test.models.MarykTypeEnum.T3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UpdateHistoryNestedScanUpdatesTest {
    @Test
    fun scanUpdatesWithUpdateHistoryIndexReturnsNestedCreateAsAddition() = runTest {
        val folder = createTestDBFolder("update-history-nested-scan-updates")
        val values = ComplexModel.create {
            multi with T3 {
                value with "u3"
                model with {
                    value with "ue3"
                }
            }
            mapStringString with mapOf("a" to "b", "c" to "d")
            mapIntObject with mapOf(
                1u to EmbeddedMarykModel.create { value with "v1" },
                2u to EmbeddedMarykModel.create { value with "v2" }
            )
            mapIntMulti with mapOf(
                1u to T3 {
                    value with "v1"
                    model with {
                        value with "sub1"
                        model with {
                            value with "sub2"
                        }
                    }
                },
                2u to T1("string")
            )
        }
        val key = ComplexModel.key(values)

        val dataStore = RocksDBDataStore.open(
            relativePath = folder,
            keepAllVersions = true,
            keepUpdateHistoryIndex = true,
            dataModelsById = dataModelsForTests,
        )

        try {
            assertIs<AddSuccess<*>>(dataStore.execute(ComplexModel.add(key to values)).statuses.first())

            val response = dataStore.execute(ComplexModel.scanUpdates(limit = 1u))

            assertIs<FetchByUpdateHistoryIndex>(response.dataFetchType)
            assertEquals(listOf(key), assertIs<OrderedKeysUpdate<ComplexModel>>(response.updates.first()).keys)
            assertEquals(
                values,
                assertIs<AdditionUpdate<ComplexModel>>(response.updates[1]).values
            )
        } finally {
            dataStore.close()
            deleteFolder(folder)
        }
    }
}
