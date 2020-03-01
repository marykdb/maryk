package maryk.datastore.test

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.getChanges
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.datastore.shared.IsDataStore
import maryk.test.assertType
import maryk.test.models.SimpleMarykModel
import maryk.test.runSuspendingTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DataStoreGetChangesUpdateTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    private val keys = mutableListOf<Key<SimpleMarykModel>>()
    private var lowestVersion = ULong.MAX_VALUE

    override val allTests = mapOf(
        "executeGetChangesAsFlowRequest" to ::executeGetChangesAsFlowRequest
    )

    override fun initData() {
        runSuspendingTest {
            val addResponse = dataStore.execute(
                SimpleMarykModel.add(
                    SimpleMarykModel(value = "haha1"),
                    SimpleMarykModel(value = "haha2"),
                    SimpleMarykModel(value = "haha3")
                )
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

    override fun resetData() {
        runSuspendingTest {
            dataStore.execute(
                SimpleMarykModel.delete(*keys.toTypedArray(), hardDelete = true)
            )
        }
        keys.clear()
        lowestVersion = ULong.MAX_VALUE
    }

    private fun executeGetChangesAsFlowRequest() = runSuspendingTest {
        val responses = arrayOf<CompletableDeferred<IsUpdateResponse<SimpleMarykModel, SimpleMarykModel.Properties>>>(
            CompletableDeferred(),
            CompletableDeferred(),
            CompletableDeferred()
        )
        var counter = 0

        val listenerSetupComplete = CompletableDeferred<Boolean>()

        val listenJob = launch {
            dataStore.executeFlow(
                SimpleMarykModel.getChanges(keys[0], keys[1])
            ).also {
                listenerSetupComplete.complete(true)
            }.collect {
                responses[counter++].complete(it)
            }
        }

        listenerSetupComplete.await()

        val successfullyDone = CompletableDeferred<Boolean>()

        val changeJob = launch {
            val change1 = Change(SimpleMarykModel { value::ref } with "haha5")
            dataStore.execute(SimpleMarykModel.change(
                keys[0].change(change1)
            ))

            val changeUpdate1 = responses[0].await()
            assertType<ChangeUpdate<*, *>>(changeUpdate1).apply {
                assertEquals(SimpleMarykModel, dataModel)
                assertEquals(keys[0], key)
                assertEquals(changes, listOf(change1))
            }

            val change2 = Change(SimpleMarykModel { value::ref } with "haha6")
            dataStore.execute(SimpleMarykModel.change(
                keys[1].change(change2)
            ))

            // This change should be ignored, otherwise key is wrong after changeUpdate2 check
            dataStore.execute(SimpleMarykModel.change(
                keys[2].change(change2)
            ))

            val changeUpdate2 = responses[1].await()
            assertType<ChangeUpdate<*, *>>(changeUpdate2).apply {
                assertEquals(SimpleMarykModel, dataModel)
                assertEquals(keys[1], key)
                assertEquals(changes, listOf(change2))
            }

            dataStore.execute(SimpleMarykModel.delete(keys[1]))

            val removalUpdate1 = responses[2].await()
            assertType<RemovalUpdate<*, *>>(removalUpdate1).apply {
                assertEquals(SimpleMarykModel, dataModel)
                assertEquals(keys[1], key)
            }

            successfullyDone.complete(true)
        }

        val timeoutJob = launch {
            // Timeout
            delay(1000)

            successfullyDone.complete(false)
        }

        val result = successfullyDone.await()

        listenJob.cancelAndJoin()
        changeJob.cancelAndJoin()
        timeoutJob.cancelAndJoin()

        assertTrue(result, message = "Expected tests to succeed")
    }
}
