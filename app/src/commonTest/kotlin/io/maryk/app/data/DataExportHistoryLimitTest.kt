package io.maryk.app.data

import kotlinx.coroutines.test.runTest
import maryk.core.exceptions.RequestException
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.responses.statuses.AddSuccess
import maryk.datastore.memory.InMemoryDataStore
import maryk.datastore.test.assertStatusIs
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertFailsWith

class DataExportHistoryLimitTest {
    @Test
    fun versionedExportRejectsRecordHistoryBeyondConfiguredLimit() = runTest {
        val store = InMemoryDataStore.open(
            keepAllVersions = true,
            dataModelsById = mapOf(1u to SimpleMarykModel),
        )
        try {
            val add = store.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha initial" }))
            val key = assertStatusIs<AddSuccess<SimpleMarykModel>>(add.statuses.single()).key
            repeat(2) { index ->
                store.execute(
                    SimpleMarykModel.change(
                        key.change(Change(SimpleMarykModel { value::ref } with "ha change $index"))
                    )
                )
            }

            assertFailsWith<RequestException> {
                loadCompleteChangesForKey(store, SimpleMarykModel, key, maxHistoryVersions = 2u)
            }
        } finally {
            store.close()
        }
    }
}
