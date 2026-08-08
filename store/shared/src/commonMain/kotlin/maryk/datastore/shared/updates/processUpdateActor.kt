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
    onFailure: suspend (Throwable) -> Unit = {},
) = coroutineScope {
    val updateListeners = mutableMapOf<UInt, MutableList<ListenerRegistration>>()

    suspend fun dispatchFlowUpdate(flowUpdate: FlowUpdate) {
        val dataModelId = dataModelIdsByString[flowUpdate.update.dataModel.Meta.name]
        val dataModelListeners = dataModelId?.let(updateListeners::get)

        if (dataModelListeners != null) {
            val iterator = dataModelListeners.iterator()
            while (iterator.hasNext()) {
                val registration = iterator.next()
                if (!registration.add(flowUpdate)) {
                    registration.cancel()
                    iterator.remove()
                }
            }
            if (dataModelListeners.isEmpty()) {
                updateListeners -= dataModelId
            }
        }
    }

    try {
        updateSendChannel.onStart { updateSendChannelHasStarted.complete(Unit) }.collect { update ->
            when (update) {
                is FlowUpdate -> {
                    dispatchFlowUpdate(update)
                }
                is Update<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    dispatchFlowUpdate(
                        FlowUpdate(update as Update<IsRootDataModel>, Long.MAX_VALUE)
                    )
                }
                is AddUpdateListenerAction -> {
                    val dataModelListeners =
                        updateListeners.getOrPut(update.dataModelId) { mutableListOf() }

                    dataModelListeners += createListenerRegistration(update.listener, this@startProcessUpdateFlow)
                    update.completion?.complete(Unit)
                }
                is AddPendingUpdateListenerAction -> {
                    if (update.listener.isCancelled()) {
                        update.completion?.completeExceptionally(
                            IllegalStateException("Pending update listener was cancelled before registration")
                        )
                    } else {
                        val dataModelListeners =
                            updateListeners.getOrPut(update.dataModelId) { mutableListOf() }

                        dataModelListeners += PendingListenerRegistration(update.listener)
                        update.completion?.complete(Unit)
                    }
                }
                is SetPendingListenerBoundaryAction -> {
                    val pendingRegistration = updateListeners[update.dataModelId]
                        ?.firstOrNull { it.matches(update.listener) }
                    if (pendingRegistration is PendingListenerRegistration) {
                        pendingRegistration.setSnapshotBoundary(update.boundary)
                    }
                    update.completion?.complete(Unit)
                }
                is ActivatePendingUpdateListenerAction -> {
                    val dataModelListeners = updateListeners[update.dataModelId]
                    val pendingIndex = dataModelListeners?.indexOfFirst { it.matches(update.pendingListener) } ?: -1
                    if (pendingIndex < 0) {
                        update.listener.close()
                        update.completion?.completeExceptionally(
                            IllegalStateException("Pending update listener was removed before activation")
                        )
                    } else {
                        try {
                            val pendingUpdates = update.pendingListener.takeUpdates()
                            val registration = createListenerRegistration(update.listener, this@startProcessUpdateFlow)
                            dataModelListeners!![pendingIndex] = registration
                            update.pendingListener.activate(update.listener)
                            pendingUpdates
                                .asSequence()
                                .forEach { bufferedUpdate ->
                                    if (!bufferedUpdate.isIncludedIn(update.snapshotBoundary) && !registration.add(bufferedUpdate)) {
                                        throw UpdateListenerOverflowException(bufferedUpdate.update.dataModel.Meta.name)
                                    }
                                }
                            update.onActivated?.invoke()
                            update.completion?.complete(Unit)
                        } catch (error: Throwable) {
                            val removed = dataModelListeners!!.removeAt(pendingIndex)
                            removed.cancel(error)
                            removed.join()
                            update.completion?.completeExceptionally(error)
                        }
                    }
                }
                is RemoveUpdateListenerAction -> {
                    val dataModelListeners = updateListeners[update.dataModelId]
                    if (dataModelListeners != null) {
                        val removedRegistrations = dataModelListeners.filter { registration ->
                            registration.matches(update.listener)
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
                is RemovePendingUpdateListenerAction -> {
                    val dataModelListeners = updateListeners[update.dataModelId]
                    val activeListener = update.listener.activatedListener.value
                    if (dataModelListeners != null) {
                        val removedRegistrations = dataModelListeners.filter { registration ->
                            registration.matches(update.listener) ||
                                (activeListener != null && registration.matches(activeListener))
                        }
                        dataModelListeners.removeAll(removedRegistrations)
                        removedRegistrations.forEach { it.cancel() }
                        removedRegistrations.forEach { it.join() }
                        if (dataModelListeners.isEmpty()) {
                            updateListeners -= update.dataModelId
                        }
                    } else {
                        update.listener.cancel()
                        activeListener?.close()
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
    } catch (error: Throwable) {
        if (error !is CancellationException) {
            onFailure(error)
        }
        throw error
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
): ActiveListenerRegistration {
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
    return ActiveListenerRegistration(originalListener, listener, updates, job)
}

private sealed interface ListenerRegistration {
    fun add(update: FlowUpdate): Boolean
    fun matches(listener: Any): Boolean
    fun cancel(cause: Throwable? = null)
    suspend fun join()
}

private class ActiveListenerRegistration(
    private val originalListener: UpdateListener<*, *>,
    val listener: UpdateListener<IsRootDataModel, *>,
    private val updates: Channel<Update<IsRootDataModel>>,
    private val job: Job,
) : ListenerRegistration {
    override fun add(update: FlowUpdate): Boolean {
        val result = updates.trySend(update.update)
        if (result.isFailure && !result.isClosed) {
            cancel(UpdateListenerOverflowException(update.update.dataModel.Meta.name))
        }
        return result.isSuccess
    }

    override fun matches(listener: Any) = originalListener === listener

    override fun cancel(cause: Throwable?) {
        job.cancel()
        updates.close()
        listener.close(cause)
    }

    override suspend fun join() = job.join()
}

private class PendingListenerRegistration(
    private val listener: PendingUpdateListener,
) : ListenerRegistration {
    fun setSnapshotBoundary(boundary: FlowSnapshotBoundary) = listener.setSnapshotBoundary(boundary)
    override fun add(update: FlowUpdate) = listener.add(update)
    override fun matches(listener: Any) = this.listener === listener
    override fun cancel(cause: Throwable?) = listener.cancel()
    override suspend fun join() = Unit
}
