package maryk.datastore.test

import kotlinx.datetime.LocalDateTime
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.filters.Equals
import maryk.core.query.orders.Direction
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.scan
import maryk.core.query.responses.FetchByTableScan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.datastore.shared.IsDataStore
import maryk.test.models.TestMarykModel
import maryk.test.models.TestMarykModel.string
import kotlin.test.expect

class DataStoreScanWithFilterTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    private val keys = mutableListOf<Key<TestMarykModel>>()
    private var lowestVersion = ULong.MAX_VALUE

    override val allTests = mapOf(
        "executeSimpleScanFilterRequest" to ::executeSimpleScanFilterRequest,
        "executeSimpleScanFilterWithToVersionRequest" to ::executeSimpleScanFilterWithToVersionRequest
    )

    private val objects = arrayOf(
        TestMarykModel(
            string="haas",
            int = 4,
            uint = 3u,
            double = 1.5,
            dateTime = LocalDateTime(2021, 1, 1, 12, 55),
            bool = false,
        )
    )

    override suspend fun initData() {
        val addResponse = dataStore.execute(
            TestMarykModel.add(*objects)
        )
        addResponse.statuses.forEach { status ->
            val response = assertStatusIs<AddSuccess<TestMarykModel>>(status)
            keys.add(response.key)
            if (response.version < lowestVersion) {
                // Add lowest version for scan test
                lowestVersion = response.version
            }
        }
    }

    override suspend fun resetData() {
        dataStore.execute(
            TestMarykModel.delete(*keys.toTypedArray(), hardDelete = true)
        ).statuses.forEach {
            assertStatusIs<DeleteSuccess<*>>(it)
        }
        keys.clear()
        lowestVersion = ULong.MAX_VALUE
    }

    private suspend fun executeSimpleScanFilterRequest() {
        val scanResponse = dataStore.execute(
            TestMarykModel.scan(
                where = Equals(
                    string.ref() with "haas"
                )
            )
        )

        expect(1) { scanResponse.values.size }
        expect(FetchByTableScan(
            direction = Direction.ASC,
            startKey = byteArrayOf(0, 0, 0, 0, 0, 0, 0),
            stopKey = byteArrayOf(-1, -1, -1, -1, -1, -1, -1),
        )) { scanResponse.dataFetchType }

        scanResponse.values[0].let {
            expect(objects[0]) { it.values }
            expect(keys[0]) { it.key }
        }
    }

    private suspend fun executeSimpleScanFilterWithToVersionRequest() {
        val changeResponse = dataStore.execute(
            TestMarykModel.change(
                keys[0].change(
                    Change(string.ref() with "haos")
                )
            )
        )

        assertStatusIs<ChangeSuccess<*>>(changeResponse.statuses[0])

        val scanResponseForLatest = dataStore.execute(
            TestMarykModel.scan(
                where = Equals(
                    string.ref() with "haas"
                )
            )
        )

        expect(0) { scanResponseForLatest.values.size }
        expect(FetchByTableScan(
            direction = Direction.ASC,
            startKey = byteArrayOf(0, 0, 0, 0, 0, 0, 0),
            stopKey = byteArrayOf(-1, -1, -1, -1, -1, -1, -1),
        )) { scanResponseForLatest.dataFetchType }

        // Only test if all versions are kept
        if (dataStore.keepAllVersions) {
            val scanResponseBeforeChange = dataStore.execute(
                TestMarykModel.scan(
                    where = Equals(
                        string.ref() with "haas"
                    ),
                    toVersion = lowestVersion
                )
            )

            expect(1) { scanResponseBeforeChange.values.size }

            scanResponseBeforeChange.values[0].let {
                expect(objects[0]) { it.values }
                expect(keys[0]) { it.key }
            }
        }
    }
}
