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
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.getChanges
import maryk.core.query.responses.statuses.AddSuccess
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.updates.Update
import maryk.datastore.shared.updates.Update.Addition
import maryk.test.assertType
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.addRequest
import maryk.test.runSuspendingTest
import kotlin.test.assertTrue

class DataStoreGetChangesUpdateTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    private val keys = mutableListOf<Key<SimpleMarykModel>>()
    private var lowestVersion = ULong.MAX_VALUE

    override val allTests = mapOf(
        "executeGetChangesAsFlowRequest" to ::executeGetChangesAsFlowRequest
    )

    fun addData() {
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
        val responses = arrayOf<CompletableDeferred<Update<*>>>(
            CompletableDeferred(),
            CompletableDeferred(),
            CompletableDeferred()
        )
        var counter = 0

        val listenerSetupComplete = CompletableDeferred<Boolean>()

        val listenJob = launch {
            dataStore.executeFlow(
                SimpleMarykModel.getChanges(*keys.toTypedArray())
            ).also {
                listenerSetupComplete.complete(true)
            }.collect {
                responses[counter++].complete(it)
            }
        }

        listenerSetupComplete.await()

        val successfullyDone = CompletableDeferred<Boolean>()

        val changeJob = launch {
            addData()

            @Suppress("UNCHECKED_CAST")
            val result1 = responses[0].await() as Addition<SimpleMarykModel>

            dataStore.execute(SimpleMarykModel.change(
                result1.key.change(
                    Change(SimpleMarykModel { value::ref } with "haha5")
                )
            ))

            responses[1].await()
            responses[2].await()

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

        assertTrue(result)
    }
}
