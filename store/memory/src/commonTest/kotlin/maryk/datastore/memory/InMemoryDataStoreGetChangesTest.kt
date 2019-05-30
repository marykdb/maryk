package maryk.datastore.memory

import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.pairs.with
import maryk.core.query.requests.getChanges
import maryk.core.query.responses.statuses.AddSuccess
import maryk.test.assertType
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.addRequest
import maryk.test.runSuspendingTest
import kotlin.test.Test
import kotlin.test.expect

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
                val response = assertType<AddSuccess<SimpleMarykModel>>(status)
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

        expect(2) { getResponse.changes.size }

        expect(
            listOf(
                VersionedChanges(version = lowestVersion, changes = listOf(
                    Change(SimpleMarykModel { value::ref} with "haha1")
                ))
            )
        ) {
            getResponse.changes[0].changes
        }

        expect(
            listOf(
                VersionedChanges(version = lowestVersion, changes = listOf(
                    Change(SimpleMarykModel { value::ref } with "haha2")
                ))
            )
        ) {
            getResponse.changes[1].changes
        }
    }

    @Test
    fun executeToVersionGetChangesRequest() = runSuspendingTest {
        val getResponse = dataStore.execute(
            SimpleMarykModel.getChanges(*keys.toTypedArray(), toVersion = lowestVersion - 1uL)
        )

        expect(0) { getResponse.changes.size }
    }

    @Test
    fun executeFromVersionGetChangesRequest() = runSuspendingTest {
        val getResponse = dataStore.execute(
            SimpleMarykModel.getChanges(*keys.toTypedArray(), fromVersion = lowestVersion + 1uL)
        )

        expect(0) { getResponse.changes.size }
    }

    @Test
    fun executeGetChangesRequestWithSelect() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            SimpleMarykModel.getChanges(
                *keys.toTypedArray(),
                select = SimpleMarykModel.graph {
                    listOf(value)
                }
            )
        )

        expect(2) { scanResponse.changes.size }

        scanResponse.changes[0].let {
            expect(
                listOf(
                    VersionedChanges(version = lowestVersion, changes = listOf(
                        Change(SimpleMarykModel { value::ref } with "haha1")
                    ))
                )
            ) {
                it.changes
            }
            expect(keys[0]) { it.key }
        }
    }
}
