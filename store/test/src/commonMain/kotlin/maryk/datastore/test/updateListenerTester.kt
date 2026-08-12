package maryk.datastore.test

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import maryk.core.models.IsRootDataModel
import maryk.core.query.requests.IsFlowRequest
import maryk.core.query.responses.IsDataResponse
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.datastore.shared.IsDataStore
import kotlin.time.Duration.Companion.seconds

/** Test helper for listening to update changes for [request] on [dataStore] */
suspend fun <DM: IsRootDataModel, RP: IsDataResponse<DM>> updateListenerTester(
    dataStore: IsDataStore,
    request: IsFlowRequest<DM, RP>,
    responseCount: Int,
    changeBlock: suspend CoroutineScope.(Array<CompletableDeferred<IsUpdateResponse<DM>>>) -> Unit
) {
    coroutineScope {
        val testDispatcher = Dispatchers.Default.limitedParallelism(1)
        val responses = Array(responseCount) {
            CompletableDeferred<IsUpdateResponse<DM>>()
        }
        var counter = 0

        val listenerSetupComplete = CompletableDeferred<Boolean>()
        val testFailure = CompletableDeferred<Throwable?>()

        val listenJob = launch(testDispatcher) {
            try {
                dataStore.executeFlow(
                    request
                ).also {
                    listenerSetupComplete.complete(true)
                }.collect {
                    val response = responses.getOrNull(counter++)
                        ?: throw AssertionError("Received more than $responseCount updates")
                    response.complete(it)
                }
            } catch (throwable: Throwable) {
                if (!listenerSetupComplete.isCompleted) {
                    listenerSetupComplete.completeExceptionally(throwable)
                } else if (throwable !is CancellationException) {
                    testFailure.complete(throwable)
                } else {
                    throw throwable
                }
            }
        }

        var changeJob: Job? = null
        var timeoutJob: Job? = null
        try {
            withContext(testDispatcher) {
                withTimeout(5.seconds) {
                    listenerSetupComplete.await()
                }
            }

            changeJob = launch(testDispatcher) {
                try {
                    changeBlock(responses)
                    testFailure.complete(null)
                } catch (e: Throwable) {
                    testFailure.complete(e)
                }
            }

            timeoutJob = launch(testDispatcher) {
                delay(5.seconds)
                testFailure.complete(
                    AssertionError("Timed out after 5s listening to updates, likely some updates were not retrieved from the store")
                )
            }

            testFailure.await()?.let { throw it }
        } finally {
            dataStore.closeAllListeners()
            listenJob.cancelAndJoin()
            changeJob?.cancelAndJoin()
            timeoutJob?.cancelAndJoin()
        }
    }
}
