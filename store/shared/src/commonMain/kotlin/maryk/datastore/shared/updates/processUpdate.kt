package maryk.datastore.shared.updates

import kotlinx.coroutines.channels.SendChannel
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.Key
import maryk.core.query.changes.IndexChange
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.requests.IsScanRequest
import maryk.core.query.requests.IsUpdatesRequest
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.RemovalReason
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.core.query.responses.updates.RemovalReason.NotInRange
import maryk.core.query.responses.updates.RemovalReason.SoftDelete
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.updates.Update.Addition
import maryk.datastore.shared.updates.Update.Change
import maryk.datastore.shared.updates.Update.Deletion

/** processes a single update */
internal suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions, RQ: IsUpdatesRequest<DM, P, *>> Update<DM, P>.process(
    updateListener: UpdateListener<DM, P, RQ>,
    dataStore: IsDataStore,
    sendChannel: SendChannel<IsUpdateResponse<DM, P>>
) {
    val request = updateListener.request
    val currentKeys = updateListener.matchingKeys
    // Only process object requests or change requests if the version is after or equal to from version
    if (request.fromVersion <= version) {
        when (this) {
            is Addition<DM, P> -> {
                if (values.matches(request.where)) {
                    val insertIndex = updateListener.addValues(key, values)
                    if (insertIndex != null) {
                        sendChannel.send(
                            AdditionUpdate(
                                key = key,
                                version = version,
                                insertionIndex = insertIndex,
                                values = values.filterWithSelect(request.select)
                            )
                        )

                        // Remove any values after the limit
                        if (updateListener is UpdateListenerForScan<DM, P> && updateListener.request.limit - 1u == insertIndex.toUInt()) {
                            val keyToRemove = updateListener.getLast()
                            updateListener.removeKey(keyToRemove)

                            sendChannel.send(
                                RemovalUpdate(
                                    key = keyToRemove,
                                    version = version,
                                    reason = NotInRange
                                )
                            )
                        }
                    }
                }
            }
            is Change<DM, P> -> {
                val shouldDelete = changes.firstOrNull { it is ObjectSoftDeleteChange }?.let { (it as ObjectSoftDeleteChange).isDeleted && request.filterSoftDeleted } ?: false

                if (currentKeys.contains(key)) {
                    if (shouldDelete) {
                        handleDeletion(dataStore, this, SoftDelete, updateListener, sendChannel)
                    } else {
                        updateListener.changeOrder(this) { newIndex, orderChanged ->
                            if (newIndex == null) {
                                handleDeletion(dataStore, this, NotInRange, updateListener, sendChannel)
                            } else {
                                createChangeUpdate<DM, P>(request.select, orderChanged, newIndex)?.let {
                                    sendChannel.send(it)
                                }
                            }
                        }
                    }
                } else if (!shouldDelete && updateListener is UpdateListenerForScan<DM, P> && updateListener.indexScanRange != null) {
                    val lastKey = updateListener.matchingKeys.last()
                    val lastSortedKey = updateListener.sortedValues?.last()

                    // Only process further if order has changed to move this value into range
                    updateListener.changeOrder(this@process) { newIndex, _ ->
                        if (newIndex != null) {
                            // Check if object matches the filter
                            val response = dataStore.execute(
                                request.dataModel.get(
                                    key,
                                    select = request.select,
                                    where = request.where,
                                    filterSoftDeleted = request.filterSoftDeleted
                                )
                            )

                            // Only handle change if it matches against key
                            if (response.values.isNotEmpty()) {
                                if (updateListener.matchingKeys.size.toUInt() >= updateListener.request.limit) {
                                    updateListener.removeKey(lastKey)

                                    sendChannel.send(
                                        RemovalUpdate(
                                            key = lastKey,
                                            version = this.version,
                                            reason = NotInRange
                                        )
                                    )
                                }

                                val addition = response.values.first()
                                sendChannel.send(
                                    AdditionUpdate(
                                        key = addition.key,
                                        values = addition.values,
                                        insertionIndex = newIndex,
                                        version = this.version
                                    )
                                )
                            } else {
                                // Add back removed values since filter does not match
                                updateListener.matchingKeys.add(lastKey)
                                lastSortedKey?.let { updateListener.sortedValues.add(it) }
                            }
                        }
                    }
                }
            }
            is Deletion<DM, P> -> {
                if (currentKeys.contains(key) && (isHardDelete || request.filterSoftDeleted)) {
                    handleDeletion(
                        dataStore,
                        this,
                        if (isHardDelete) HardDelete else SoftDelete,
                        updateListener,
                        sendChannel
                    )
                }
            }
        }
    }
}

private fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> Change<DM, P>.createChangeUpdate(
    select: RootPropRefGraph<P>?,
    orderChanged: Boolean,
    newIndex: Int
): ChangeUpdate<DM, P>? {
    // Filter now in search of possible delete
    val filteredChanges = changes.filterWithSelect(select)

    // Only send ChangeUpdate if order has changed or there are changes with are not just index changes
    // IndexChanges are covered with orderChanged check so filteredChanges need to contain more than IndexChanges
    if (orderChanged || (filteredChanges.isNotEmpty() && filteredChanges.find { it !is IndexChange } != null)) {
        return ChangeUpdate(
            key = key,
            version = version,
            index = newIndex,
            changes = filteredChanges
        )
    }

    return null
}

/** Handles the deletion of Values defined in [change] and if necessary request a new value to put at end */
private suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions, RQ: IsUpdatesRequest<DM, P, *>> handleDeletion(
    dataStore: IsDataStore,
    change: Update<DM, P>,
    reason: RemovalReason,
    updateListener: UpdateListener<DM, P, RQ>,
    sendChannel: SendChannel<IsUpdateResponse<DM, P>>
) {
    val originalIndex = updateListener.removeKey(change.key)

    suspend fun sendRemoval() {
        sendChannel.send(
            RemovalUpdate(
                key = change.key,
                version = change.version,
                reason = reason
            )
        )
    }

    if (updateListener is UpdateListenerForScan<DM, P> && updateListener.request.limit - 1u == updateListener.matchingKeys.size.toUInt()) {
        dataStore.requestNextValues(updateListener.request, updateListener.matchingKeys)?.also { additionUpdate ->
            // Always at the end so no need to order
            updateListener.addValuesAtEnd(additionUpdate.key, additionUpdate.values)

            if (change is Change<DM, P>) {
                if (additionUpdate.key != change.key) { // if not same key, remove old & add new
                    sendRemoval()
                    sendChannel.send(additionUpdate)
                } else if (originalIndex != additionUpdate.insertionIndex) {
                    // If same key send an order change
                    change.createChangeUpdate(
                        updateListener.request.select,
                        orderChanged = true,
                        newIndex = additionUpdate.insertionIndex
                    )?.also { sendChannel.send(it) }
                } // else no change because still the last
            } else {
                sendRemoval()
                sendChannel.send(additionUpdate)
            }
        } ?: sendRemoval()
    } else {
        sendRemoval()
    }
}

/** Requests next values object after last key in [currentKeys] */
private suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> IsDataStore.requestNextValues(
    request: IsScanRequest<DM, P, *>,
    currentKeys: MutableList<Key<DM>>
): AdditionUpdate<DM, P>? {
    val nextResults = execute(
        request.dataModel.scan(
            currentKeys.last(),
            request.select,
            where = request.where,
            order = request.order,
            limit = 1u,
            includeStart = false,
            toVersion = request.toVersion,
            filterSoftDeleted = request.filterSoftDeleted
        )
    )

    return if (nextResults.values.isNotEmpty()) {
        val nextValues = nextResults.values.first()

        AdditionUpdate(
            key = nextValues.key,
            version = nextValues.lastVersion,
            insertionIndex = currentKeys.size,
            values = nextValues.values
        )
    } else null
}

/** Filters a list of changes to only have changes to properties defined in [select] */
private fun List<IsChange>.filterWithSelect(
    select: RootPropRefGraph<out PropertyDefinitions>?,
    changeProcessor: ((IsChange) -> Unit)? = null
): List<IsChange> {
    if (select == null) {
        // process all changes
        changeProcessor?.let(this::forEach)
        return this
    }
    return this.mapNotNull { change: IsChange ->
        changeProcessor?.invoke(change)
        change.filterWithSelect(select)
    }
}

