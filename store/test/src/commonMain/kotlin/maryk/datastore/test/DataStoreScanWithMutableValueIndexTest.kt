package maryk.datastore.test

import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.orders.ascending
import maryk.core.query.orders.descending
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.datastore.shared.IsDataStore
import maryk.test.assertType
import maryk.test.models.ModelV2ExtraIndex
import maryk.test.models.SimpleMarykModel
import kotlin.test.expect

class DataStoreScanWithMutableValueIndexTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    private val keys = mutableListOf<Key<ModelV2ExtraIndex>>()
    private var lowestVersion = ULong.MAX_VALUE

    override val allTests = mapOf(
        "executeScanOnAscendingIndexRequest" to ::executeScanOnAscendingIndexRequest,
        "executeScanChangesOnDescendingIndexRequest" to ::executeScanOnDescendingIndexRequest
    )

    private val objects = arrayOf(
        ModelV2ExtraIndex("ha1", 5),
        ModelV2ExtraIndex("ha2", 2),
        ModelV2ExtraIndex("ha3", 7),
        ModelV2ExtraIndex("ha4", 1)
    )

    override suspend fun initData() {
        val addResponse = dataStore.execute(
            ModelV2ExtraIndex.add(*objects)
        )
        addResponse.statuses.forEach { status ->
            val response = assertType<AddSuccess<ModelV2ExtraIndex>>(status)
            keys.add(response.key)
            if (response.version < lowestVersion) {
                // Add lowest version for scan test
                lowestVersion = response.version
            }
        }
    }

    override suspend fun resetData() {
        dataStore.execute(
            ModelV2ExtraIndex.delete(*keys.toTypedArray(), hardDelete = true)
        )
        keys.clear()
        lowestVersion = ULong.MAX_VALUE
    }

    private suspend fun executeScanOnAscendingIndexRequest() {
        val changeResult = dataStore.execute(
            ModelV2ExtraIndex.change(
                keys[2].change(Change(ModelV2ExtraIndex { newNumber::ref } with 99))
            )
        )

        var versionAfterChange = 0uL
        for (status in changeResult.statuses) {
            assertType<ChangeSuccess<SimpleMarykModel>>(status).apply {
                versionAfterChange = this.version
            }
        }

        val scanResponse = dataStore.execute(
            ModelV2ExtraIndex.scan(startKey = keys[1], order = ModelV2ExtraIndex { newNumber::ref }.ascending())
        )

        expect(3) { scanResponse.values.size }

        scanResponse.values[0].apply {
            expect(keys[1]) { key }
        }
        scanResponse.values[1].apply {
            expect(keys[0]) { key }
        }
        scanResponse.values[2].apply {
            expect(keys[2]) { key }
        }

        if (dataStore.keepAllVersions) {
            val historicScanResponse = dataStore.execute(
                ModelV2ExtraIndex.scan(
                    startKey = keys[1],
                    order = ModelV2ExtraIndex { newNumber::ref }.ascending(),
                    toVersion = versionAfterChange - 1uL // Before the change
                )
            )

            expect(3) { historicScanResponse.values.size }

            historicScanResponse.values[0].apply {
                expect(keys[1]) { key }
            }
            historicScanResponse.values[1].apply {
                expect(keys[0]) { key }
            }
            historicScanResponse.values[2].apply {
                expect(keys[2]) { key }
            }
        }
    }

    private suspend fun executeScanOnDescendingIndexRequest() {
        val scanResponse = dataStore.execute(
            ModelV2ExtraIndex.scan(startKey = keys[1], order = ModelV2ExtraIndex { newNumber::ref }.descending())
        )

        expect(2) { scanResponse.values.size }

        scanResponse.values[0].apply {
            expect(keys[1]) { key }
        }
        scanResponse.values[1].apply {
            expect(keys[3]) { key }
        }
    }
}
