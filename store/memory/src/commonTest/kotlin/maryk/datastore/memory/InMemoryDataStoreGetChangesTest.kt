@file:Suppress("EXPERIMENTAL_API_USAGE")

package maryk.datastore.memory

import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.pairs.with
import maryk.core.query.requests.getChanges
import maryk.core.query.responses.statuses.AddSuccess
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.addRequest
import maryk.test.runSuspendingTest
import maryk.test.shouldBe
import maryk.test.shouldBeOfType
import kotlin.test.Test

@Suppress("EXPERIMENTAL_UNSIGNED_LITERALS")
class InMemoryDataStoreGetChangesTest {
    private val dataStore = InMemoryDataStore()
    private val keys = mutableListOf<Key<SimpleMarykModel>>()
    private var lowestVersion = ULong.MAX_VALUE

    init {
        runSuspendingTest {
            val addResponse = dataStore.execute(
                addRequest
            )
            addResponse.statuses.forEach { status ->
                val response = shouldBeOfType<AddSuccess<SimpleMarykModel>>(status)
                keys.add(response.key)
                if (response.version < lowestVersion) {
                    // Add lowest version for scan test
                    lowestVersion = response.version
                }
            }
        }
    }

    @Test
    fun executeSimpleGetChangesRequest() = runSuspendingTest {
        val getResponse = dataStore.execute(
            SimpleMarykModel.getChanges(*keys.toTypedArray())
        )

        getResponse.changes.size shouldBe 2

        getResponse.changes[0].changes shouldBe listOf(
            VersionedChanges(version = lowestVersion, changes = listOf(
                Change(SimpleMarykModel.ref { value } with "haha1")
            ))
        )

        getResponse.changes[1].changes shouldBe listOf(
            VersionedChanges(version = lowestVersion, changes = listOf(
                Change(SimpleMarykModel.ref { value } with "haha2")
            ))
        )
    }

    @Test
    fun executeToVersionGetChangesRequest() = runSuspendingTest {
        val getResponse = dataStore.execute(
            SimpleMarykModel.getChanges(*keys.toTypedArray(), toVersion = lowestVersion - 1uL)
        )

        getResponse.changes.size shouldBe 0
    }
}
