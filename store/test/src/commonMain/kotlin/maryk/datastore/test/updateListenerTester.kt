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
import maryk.core.query.requests.IsFetchRequest
import maryk.core.query.responses.IsDataResponse
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.datastore.shared.IsDataStore
import kotlin.test.assertTrue

/** Test helper for listening to update changes for [request] on [dataStore] */
suspend fun <DM: IsRootDataModel, RP: IsDataResponse<DM>> updateListenerTester(
    dataStore: IsDataStore,
    request: IsFetchRequest<DM, RP>,
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

    val successfullyDone = CompletableDeferred<Boolean>()

    val changeJob = GlobalScope.launch {
        try {
            changeBlock(responses)
            successfullyDone.complete(true)
        } catch (e: Throwable) {
            e.printStackTrace()
            successfullyDone.complete(false)
        }
    }

    val timeoutJob = GlobalScope.launch {
        // Timeout
        delay(1000)
        println("  TIMEOUT LISTENING TO UPDATES, likely some updates were not retrieved from the store")

        successfullyDone.complete(false)
    }

    val result = successfullyDone.await()

    dataStore.closeAllListeners()

    listenJob.cancelAndJoin()
    changeJob.cancelAndJoin()
    timeoutJob.cancelAndJoin()

    assertTrue(result, message = "Expected tests to succeed")
}
