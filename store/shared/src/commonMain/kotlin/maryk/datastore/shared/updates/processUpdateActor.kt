package maryk.datastore.shared.updates

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import maryk.core.models.IsRootDataModel
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.rethrowIfFatal

internal suspend fun IsDataStore.startProcessUpdateFlow(
    updateSendChannel: Flow<IsUpdateAction>,
    updateSendChannelHasStarted: CompletableDeferred<Unit>,
) = coroutineScope {
    val updateListeners = mutableMapOf<UInt, MutableList<ListenerRegistration>>()

    try {
        updateSendChannel.onStart { updateSendChannelHasStarted.complete(Unit) }.collect { update ->
            when (update) {
                is Update<*> -> {
                    val dataModelId = dataModelIdsByString[update.dataModel.Meta.name]
                    val dataModelListeners = dataModelId?.let(updateListeners::get)

                    if (dataModelListeners != null) {
                        val iterator = dataModelListeners.iterator()
                        while (iterator.hasNext()) {
                            val registration = iterator.next()
                            @Suppress("UNCHECKED_CAST")
                            val result = registration.updates.trySend(update as Update<IsRootDataModel>)
                            if (result.isFailure) {
                                if (!result.isClosed) {
                                    registration.cancel(
                                        UpdateListenerOverflowException(update.dataModel.Meta.name)
                                    )
                                }
                                iterator.remove()
                            }
                        }
                        if (dataModelListeners.isEmpty()) {
                            updateListeners -= dataModelId
                        }
                    }
                }
                is AddUpdateListenerAction -> {
                    val dataModelListeners =
                        updateListeners.getOrPut(update.dataModelId) { mutableListOf() }

                    dataModelListeners += createListenerRegistration(update.listener, this@startProcessUpdateFlow)
                    update.completion?.complete(Unit)
                }
                is RemoveUpdateListenerAction -> {
                    val dataModelListeners = updateListeners[update.dataModelId]
                    if (dataModelListeners != null) {
                        val removedRegistrations = dataModelListeners.filter { registration ->
                            registration.originalListener === update.listener
                        }
                        dataModelListeners.removeAll(removedRegistrations)
                        removedRegistrations.forEach { it.cancel() }
                        removedRegistrations.forEach { it.join() }
                        if (dataModelListeners.isEmpty()) {
                            updateListeners -= update.dataModelId
                        }
                    } else {
                        update.listener.close()
                    }
                    update.completion?.complete(Unit)
                }
                is RemoveAllUpdateListenersAction -> {
                    val registrations = updateListeners.values.flatten()
                    updateListeners.clear()
                    registrations.forEach { it.cancel() }
                    registrations.forEach { it.join() }
                    update.completion?.complete(Unit)
                }
                else -> throw RuntimeException("Unknown update listener action: $update")
            }
        }
    } finally {
        val registrations = updateListeners.values.flatten()
        updateListeners.clear()
        registrations.forEach { it.cancel() }
        registrations.forEach { it.join() }
    }
}

private fun CoroutineScope.createListenerRegistration(
    originalListener: UpdateListener<*, *>,
    dataStore: IsDataStore,
): ListenerRegistration {
    @Suppress("UNCHECKED_CAST")
    val listener = originalListener as UpdateListener<IsRootDataModel, *>
    val updates = Channel<Update<IsRootDataModel>>(UPDATE_LISTENER_MAILBOX_CAPACITY)
    val job = launch {
        try {
            for (update in updates) {
                listener.process(update, dataStore)
            }
        } catch (error: CancellationException) {
            if (currentCoroutineContext().isActive) {
                this@createListenerRegistration.cancel(error)
            }
            throw error
        } catch (error: Throwable) {
            error.rethrowIfFatal()
            updates.close(error)
            listener.close(UpdateListenerProcessingException(listener.request.dataModel.Meta.name, error))
        }
    }
    return ListenerRegistration(originalListener, listener, updates, job)
}

private class ListenerRegistration(
    val originalListener: UpdateListener<*, *>,
    private val listener: UpdateListener<IsRootDataModel, *>,
    val updates: Channel<Update<IsRootDataModel>>,
    private val job: Job,
) {
    fun cancel(cause: Throwable? = null) {
        job.cancel()
        updates.close()
        listener.close(cause)
    }

    suspend fun join() = job.join()
}

private const val UPDATE_LISTENER_MAILBOX_CAPACITY = 64
