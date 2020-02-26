package maryk.datastore.test

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import maryk.core.exceptions.RequestException
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.getChanges
import maryk.core.query.responses.statuses.AddSuccess
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.Update
import maryk.datastore.shared.Update.Addition
import maryk.test.assertType
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.addRequest
import maryk.test.runSuspendingTest
import kotlin.test.assertFailsWith
import kotlin.test.expect

class DataStoreGetChangesTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    private val keys = mutableListOf<Key<SimpleMarykModel>>()
    private var lowestVersion = ULong.MAX_VALUE

    override val allTests = mapOf(
        "executeSimpleGetChangesRequest" to ::executeSimpleGetChangesRequest,
        "executeToVersionGetChangesRequest" to ::executeToVersionGetChangesRequest,
        "executeFromVersionGetChangesRequest" to ::executeFromVersionGetChangesRequest,
        "executeGetChangesRequestWithSelect" to ::executeGetChangesRequestWithSelect,
        "executeGetChangesAsFlowRequest" to ::executeGetChangesAsFlowRequest
    )

    override fun initData() {
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

    private fun executeSimpleGetChangesRequest() = runSuspendingTest {
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

    private fun executeToVersionGetChangesRequest() = runSuspendingTest {
        if (dataStore.keepAllVersions) {
            val getResponse = dataStore.execute(
                SimpleMarykModel.getChanges(*keys.toTypedArray(), toVersion = lowestVersion - 1uL)
            )

            expect(0) { getResponse.changes.size }
        } else {
            assertFailsWith<RequestException> {
                runSuspendingTest {
                    dataStore.execute(
                        SimpleMarykModel.getChanges(*keys.toTypedArray(), toVersion = lowestVersion - 1uL)
                    )
                }
            }
        }
    }

    private fun executeFromVersionGetChangesRequest() = runSuspendingTest {
        val getResponse = dataStore.execute(
            SimpleMarykModel.getChanges(*keys.toTypedArray(), fromVersion = lowestVersion + 1uL)
        )

        expect(0) { getResponse.changes.size }
    }

    private fun executeGetChangesRequestWithSelect() = runSuspendingTest {
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

    private fun executeGetChangesAsFlowRequest() = runSuspendingTest {
        val responses = arrayOf<CompletableDeferred<Update>>(
            CompletableDeferred(),
            CompletableDeferred(),
            CompletableDeferred()
        )
        var counter = 0

        val scope = object : CoroutineScope {
            override val coroutineContext = Dispatchers.Default + Job()
        }
        scope.launch {
            dataStore.executeFlow(
                SimpleMarykModel.getChanges(*keys.toTypedArray())
            ).collect {
                responses[counter++].complete(it)
            }
        }

        dataStore.execute(SimpleMarykModel.add(
            SimpleMarykModel(value = "haha4"),
            SimpleMarykModel(value = "haha5")
        ))

        val result1 = responses[0].await()
        val result2 = responses[1].await()

        @Suppress("UNCHECKED_CAST")
        dataStore.execute(SimpleMarykModel.change(
            ((result1 as Addition).key as Key<SimpleMarykModel>).change(
                Change(SimpleMarykModel { value::ref } with "haha5")
            )
        ))

        val result3 = responses[2].await()

        println("mooi "+result2 +" "+result3)
        scope.coroutineContext.cancel()
    }
}
