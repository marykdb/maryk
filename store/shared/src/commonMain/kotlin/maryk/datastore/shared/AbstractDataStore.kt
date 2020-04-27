package maryk.datastore.shared

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.RootDataModel
import maryk.core.models.fromChanges
import maryk.core.processors.datastore.scanRange.createScanRange
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.IsReferenceDefinition
import maryk.core.properties.graph.IsPropRefGraphNode
import maryk.core.properties.types.Key
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.orders.Order
import maryk.core.query.orders.Orders
import maryk.core.query.requests.GetChangesRequest
import maryk.core.query.requests.IsChangesRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.requests.ScanChangesRequest
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.responses.ChangesResponse
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.core.query.responses.updates.RemovalReason.NotInRange
import maryk.core.query.responses.updates.RemovalReason.SoftDelete
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.datastore.shared.updates.Update.Addition
import maryk.datastore.shared.updates.Update.Change
import maryk.datastore.shared.updates.Update.Deletion
import maryk.datastore.shared.updates.UpdateListener
import maryk.datastore.shared.updates.UpdateListenerForGet
import maryk.datastore.shared.updates.UpdateListenerForScan
import maryk.datastore.shared.updates.process
import maryk.datastore.shared.updates.processUpdateActor

typealias StoreActor = SendChannel<StoreAction<*, *, *, *>>

/**
 * Abstract DataStore implementation that takes care of the HLC clock
 */
abstract class AbstractDataStore(
    final override val dataModelsById: Map<UInt, RootDataModel<*, *>>
): IsDataStore, CoroutineScope {
    override val coroutineContext = DISPATCHER + SupervisorJob()

    val updateListeners = mutableMapOf<UInt, MutableList<UpdateListener<*, *, *>>>()
    val updateSendChannel = processUpdateActor<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>()

    /** StoreActor to run actions against.*/
    abstract val storeActor: StoreActor

    override val dataModelIdsByString = dataModelsById.map { (index, dataModel) ->
        Pair(dataModel.name, index)
    }.toMap()

    // Clock actor holds/calculates the latest HLC clock instance
    private val clockActor = this.clockActor()

    override suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
        request: RQ
    ): RP {
        val response = CompletableDeferred<RP>()

        val clock = DeferredClock().also {
            clockActor.send(it)
        }.completableDeferred.await()

        storeActor.send(
            StoreAction(clock, request, response)
        )

        return response.await()
    }

    @FlowPreview
    @ExperimentalCoroutinesApi
    override suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions, RQ> executeFlow(
        request: RQ,
        orderedKeys: List<Key<DM>>?
    ): Flow<IsUpdateResponse<DM, P>>
        where RQ : IsStoreRequest<DM, ChangesResponse<DM>>, RQ: IsChangesRequest<DM, P, ChangesResponse<DM>> {
        if (request.toVersion != null) {
            throw RequestException("Cannot use toVersion on an executeFlow request")
        }

        // Don't allow filters with mutable or reference values
        request.where?.singleReference {
            !it.propertyDefinition.required || !it.propertyDefinition.final || it.comparablePropertyDefinition is IsReferenceDefinition<*, *, *>
        }?.let {
            throw RequestException("$it is mutable or a reference which are not supported on filters in update listeners.")
        }

        val channel = BroadcastChannel<IsUpdateResponse<DM, P>>(Channel.BUFFERED)

        val dataModelId = getDataModelId(request.dataModel)

        val dataModelUpdateListeners = this.updateListeners.getOrPut(dataModelId) { mutableListOf() }
        val listener = request.createUpdateListener(this, channel)

        dataModelUpdateListeners += listener

        val response = execute(request)

        return channel.asFlow().onCompletion {
            listener.close()
            dataModelUpdateListeners -= listener
        }.onStart {
            emit(
                OrderedKeysUpdate(
                    version = listener.lastResponseVersion,
                    keys = listener.matchingKeys
                )
            )

            // Emit first all new changes after passed firstVersion
            val sortedChanges = if (response.changes.isNotEmpty()) {
                response.changes.flatMap { dataObjectVersionedChange ->
                    dataObjectVersionedChange.changes.map { versionedChange ->
                        val changes = versionedChange.changes

                        if (changes.contains(ObjectCreate)) {
                            val addedValues = request.dataModel.fromChanges(null, changes)

                            Addition(
                                response.dataModel,
                                dataObjectVersionedChange.key,
                                versionedChange.version,
                                addedValues
                            )
                        } else if (request.filterSoftDeleted && changes.firstOrNull { it is ObjectSoftDeleteChange } != null) {
                            Deletion(
                                response.dataModel,
                                dataObjectVersionedChange.key,
                                versionedChange.version,
                                false
                            )
                        } else {
                            Change(
                                response.dataModel,
                                dataObjectVersionedChange.key,
                                versionedChange.version,
                                changes.toList()
                            )
                        }
                    }
                }.sortedBy {
                    it.version
                }
            } else emptyList()

            val addedKeys = if (orderedKeys != null) mutableListOf<Key<DM>>() else null
            val removedKeys = if (orderedKeys != null) mutableListOf<Key<DM>>() else null

            // First walk the updates of before the ordered keys change. Set index always at index found in ordered
            // keys to prevent jumping around
            var sortedLoopIndex = 0
            while (sortedLoopIndex < sortedChanges.size) {
                val update = sortedChanges[sortedLoopIndex++]
                if (update.version <= listener.lastResponseVersion) {
                    // Was already in OrderedKeysUpdate so find index to send to client
                    val index = listener.matchingKeys.indexOf(update.key)

                    if (index >= 0) {
                        when (update) {
                            is Addition<DM, P> -> {
                                addedKeys?.add(update.key)
                                AdditionUpdate(update.key, update.version, index, update.values)
                            }
                            is Deletion -> {
                                removedKeys?.add(update.key)
                                RemovalUpdate(update.key, update.version, if(update.isHardDelete) HardDelete else SoftDelete)
                            }
                            is Change -> {
                                if (orderedKeys?.contains(update.key) != true && addedKeys?.contains(update.key) != true) {
                                    execute(
                                        request.dataModel.get(
                                            update.key, select = request.select, where = request.where, filterSoftDeleted = request.filterSoftDeleted
                                        )
                                    ).values.firstOrNull()?.let {
                                        addedKeys?.add(update.key)
                                        AdditionUpdate(update.key, update.version, index, it.values)
                                    }
                                } else {
                                    ChangeUpdate(update.key, update.version, index, update.changes)
                                }
                            }
                        }.apply { this?.let { emit(this) } }
                    }
                } else {
                    break
                }
            }

            // Add and remove values which should or should not be there from passed orderedKeys
            // This so the requester is up to date with any in between filtered values
            orderedKeys?.let {
                for (removedKey in orderedKeys.subtract(listener.matchingKeys).subtract(removedKeys!!)) {
                    emit(
                        RemovalUpdate(
                            key = removedKey,
                            version = listener.lastResponseVersion,
                            reason = NotInRange
                        )
                    )
                }
                val keysToAdd = listener.matchingKeys.subtract(orderedKeys).subtract(addedKeys!!)
                val toBeAddedResponse = execute(
                    request.dataModel.get(
                        *keysToAdd.toTypedArray(),
                        select = request.select,
                        where = request.where,
                        filterSoftDeleted = request.filterSoftDeleted
                    )
                )

                for (value in toBeAddedResponse.values) {
                    emit(
                        AdditionUpdate(
                            value.key,
                            version = listener.lastResponseVersion,
                            insertionIndex = listener.matchingKeys.indexOf(value.key),
                            values = value.values
                        )
                    )
                }
            }

            // Walk items after version of OrderedKeys as if they are normal changes
            while (sortedLoopIndex < sortedChanges.size) {
                val update = sortedChanges[sortedLoopIndex++]
                @Suppress("UNCHECKED_CAST")
                update.process(listener as UpdateListener<DM, P, RQ>, this@AbstractDataStore, listener.sendChannel)
            }
        }
    }

    /** Get [dataModel] id to identify it for storage */
    fun getDataModelId(dataModel: IsRootValuesDataModel<*>) =
        dataModelIdsByString[dataModel.name] ?:
        throw DefNotFoundException("DataStore not found ${dataModel.name}")

    override fun close() {
        this.cancel()

        clockActor.close()

        updateSendChannel.close()
        updateListeners.values.forEach { it.forEach(UpdateListener<*, *, *>::close) }
        updateListeners.clear()
    }
}

/** Creates update listener for request on [channel] */
private suspend fun <DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> IsChangesRequest<DM, P, *>.createUpdateListener(
    dataStore: IsDataStore,
    channel: SendChannel<IsUpdateResponse<DM, P>>
) =
    when (this) {
        is ScanChangesRequest<DM, P> -> {
            val scanResponse = dataStore.execute(
                dataModel.scan(
                    startKey,
                    dataModel.graph {
                        @Suppress("UNCHECKED_CAST")
                        when (order) {
                            is Order ->
                                listOfNotNull((order as Order).propertyReference?.propertyDefinition as? IsPropRefGraphNode<P>?)
                            is Orders ->
                                (order as Orders).orders.mapNotNull { it.propertyReference?.propertyDefinition as? IsPropRefGraphNode<P> }
                            else -> emptyList()
                        }
                    },
                    where,
                    order,
                    limit,
                    includeStart,
                    filterSoftDeleted = filterSoftDeleted
                )
            )
            UpdateListenerForScan(
                request = this,
                scanRange = this.dataModel.createScanRange(this.where, this.startKey?.bytes, this.includeStart),
                scanResponse = scanResponse,
                sendChannel = channel
            )
        }
        is GetChangesRequest<DM, P> -> {
            val getResponse = dataStore.execute(
                dataModel.get(
                    *keys.toTypedArray(),
                    select = dataModel.graph { emptyList() },
                    where = where,
                    filterSoftDeleted = filterSoftDeleted
                )
            )
            UpdateListenerForGet(
                this,
                getResponse,
                channel
            )
        }
        else -> throw RequestException("Unsupported request type for update listener: $this")
    }
