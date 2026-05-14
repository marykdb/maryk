@file:OptIn(DelicateCoroutinesApi::class)

package maryk.datastore.test

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import maryk.core.models.IsRootDataModel
import maryk.core.query.requests.IsFlowRequest
import maryk.core.query.responses.IsDataResponse
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.datastore.shared.IsDataStore
import kotlin.time.Duration.Companion.milliseconds

/** Test helper for listening to update changes for [request] on [dataStore] */
suspend fun <DM: IsRootDataModel, RP: IsDataResponse<DM>> updateListenerTester(
    dataStore: IsDataStore,
    request: IsFlowRequest<DM, RP>,
    responseCount: Int,
    changeBlock: suspend CoroutineScope.(Array<CompletableDeferred<IsUpdateResponse<DM>>>) -> Unit
) {
    val responses = Array(responseCount) {
        CompletableDeferred<IsUpdateResponse<DM>>()
    }
    var counter = 0

    val listenerSetupComplete = CompletableDeferred<Boolean>()

    val listenJob = GlobalScope.launch {
        dataStore.executeFlow(
            request
        ).also {
            listenerSetupComplete.complete(true)
        }.collect {
            responses[counter++].complete(it)
        }
    }

    listenerSetupComplete.await()

    val testFailure = CompletableDeferred<Throwable?>()

    val changeJob = GlobalScope.launch {
        try {
            changeBlock(responses)
            testFailure.complete(null)
        } catch (e: Throwable) {
            testFailure.complete(e)
        }
    }

    val timeoutJob = GlobalScope.launch {
        // Timeout
        delay(15000.milliseconds)
        testFailure.complete(
            AssertionError("Timed out after 15000ms listening to updates, likely some updates were not retrieved from the store")
        )
    }

    val failure = testFailure.await()

    dataStore.closeAllListeners()

    listenJob.cancelAndJoin()
    changeJob.cancelAndJoin()
    timeoutJob.cancelAndJoin()

    if (failure != null) {
        throw failure
    }
}
