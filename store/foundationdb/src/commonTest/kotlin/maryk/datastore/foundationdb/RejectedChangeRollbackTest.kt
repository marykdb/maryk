package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.core.query.changes.Change
import maryk.core.query.changes.IndexChange
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ServerFail
import maryk.test.models.SimpleMarykModel
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RejectedChangeRollbackTest {
    @Test
    fun unsupportedChangeAfterValueMutationDoesNotCommitPartialData() = runTest {
        val store = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "unsupported-change-rollback-${Random.nextInt()}"),
            dataModelsById = mapOf(1u to SimpleMarykModel), keepAllVersions = true)
        try {
            val added = assertIs<AddSuccess<SimpleMarykModel>>(store.execute(SimpleMarykModel.add(
                SimpleMarykModel.create { value with "ha-before" }
            )).statuses.single())
            assertIs<ServerFail<SimpleMarykModel>>(store.execute(SimpleMarykModel.change(added.key.change(
                Change(SimpleMarykModel.ref { value } with "ha-after"), IndexChange(emptyList())
            ))).statuses.single())
            val current = store.execute(SimpleMarykModel.get(added.key)).values.single()
            assertEquals("ha-before", current.values { value })
            assertEquals(added.version, current.lastVersion)
        } finally {
            store.close()
        }
    }
}
