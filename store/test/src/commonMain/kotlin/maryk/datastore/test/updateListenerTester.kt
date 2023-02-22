package maryk.datastore.test

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import maryk.core.models.IsRootDataModel
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.query.requests.IsFetchRequest
import maryk.core.query.responses.IsDataResponse
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.datastore.shared.IsDataStore
import maryk.test.runSuspendingTest
import kotlin.test.assertTrue

/** Test helper for listening to update changes for [request] on [dataStore] */
fun <DM: IsRootDataModel<P>, P: IsValuesPropertyDefinitions, RP: IsDataResponse<DM, P>> updateListenerTester(
    dataStore: IsDataStore,
    request: IsFetchRequest<DM, P, RP>,
    responseCount: Int,
    changeBlock: suspend CoroutineScope.(Array<CompletableDeferred<IsUpdateResponse<DM, P>>>) -> Unit
) = runSuspendingTest {
    val responses = Array(responseCount) {
        CompletableDeferred<IsUpdateResponse<DM, P>>()
    }
    var counter = 0

    val listenerSetupComplete = CompletableDeferred<Boolean>()

    val listenJob = launch {
        dataStore.executeFlow(
            request
        ).also {
            listenerSetupComplete.complete(true)
        }.collect {
            responses[counter++].complete(it)
        }
    }

    listenerSetupComplete.await()

    val successfullyDone = CompletableDeferred<Boolean>()

    val changeJob = launch {
        changeBlock(responses)

        successfullyDone.complete(true)
    }

    val timeoutJob = launch {
        // Timeout
        delay(1000)

        successfullyDone.complete(false)
    }

    val result = successfullyDone.await()

    dataStore.closeAllListeners()

    listenJob.cancelAndJoin()
    changeJob.cancelAndJoin()
    timeoutJob.cancelAndJoin()

    assertTrue(result, message = "Expected tests to succeed")
}
