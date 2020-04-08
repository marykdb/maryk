package maryk.datastore.shared.updates

import kotlinx.coroutines.channels.SendChannel
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.Key
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.requests.IsChangesRequest
import maryk.core.query.requests.IsScanRequest
import maryk.core.query.requests.scan
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.updates.Update.Addition
import maryk.datastore.shared.updates.Update.Change
import maryk.datastore.shared.updates.Update.Deletion

/** processes a single update */
internal suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions, RQ: IsChangesRequest<DM, P, *>> Update<DM, P>.process(
    updateListener: UpdateListener<DM, P, RQ>,
    dataStore: IsDataStore,
    sendChannel: SendChannel<IsUpdateResponse<DM, P>>
) {
    val request = updateListener.request
    val currentKeys = updateListener.matchingKeys
    // Only process object requests or change requests if the version is after or equal to from version
    if (request.fromVersion <= version.timestamp) {
        when (this) {
            is Addition<DM, P> -> {
                if (values.matches(request.where)) {
                    val insertIndex = updateListener.addValues(key, values)
                    if (insertIndex != null) {
                        sendChannel.send(
                            AdditionUpdate(
                                key = key,
                                version = version.timestamp,
                                insertionIndex = insertIndex,
                                values = values.filterWithSelect(request.select)
                            )
                        )

                        // Remove any values after the limit
                        if (updateListener is UpdateListenerForScan<DM, P> && updateListener.request.limit - 1u == insertIndex.toUInt()) {
                            val keyToRemove = updateListener.matchingKeys.last()
                            updateListener.removeKey(keyToRemove)

                            sendChannel.send(
                                RemovalUpdate(
                                    key = keyToRemove,
                                    version = version.timestamp
                                )
                            )
                        }
                    }
                }
            }
            is Change<DM, P> -> {
                if (currentKeys.contains(key)) {
                    var shouldDelete = false
                    val filteredChanges = changes.filterWithSelect(request.select) {
                        if (it is ObjectSoftDeleteChange && it.isDeleted) {
                            shouldDelete = true
                        }
                    }

                    if (shouldDelete) {
                        handleDeletion(dataStore, this, updateListener, sendChannel)
                    } else {
                        // TODO Reorder?
                        sendChannel.send(
                            ChangeUpdate(
                                key = key,
                                version = version.timestamp,
                                changes = filteredChanges
                            )
                        )
                    }
                }
            }
            is Deletion<DM, P> -> {
                if (currentKeys.contains(key) && (isHardDelete || request.filterSoftDeleted)) {
                    handleDeletion(dataStore, this, updateListener, sendChannel)
                }
            }
        }
    }
}

/** Handles the deletion of Values defined in [change] and if necessary request a new value to put at end */
private suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions, RQ: IsChangesRequest<DM, P, *>> handleDeletion(
    dataStore: IsDataStore,
    change: Update<DM, P>,
    updateListener: UpdateListener<DM, P, RQ>,
    sendChannel: SendChannel<IsUpdateResponse<DM, P>>
) {
    updateListener.removeKey(change.key)

    sendChannel.send(
        RemovalUpdate(
            key = change.key,
            version = change.version.timestamp
        )
    )

    if (updateListener is UpdateListenerForScan<DM, P> && updateListener.request.limit - 1u == updateListener.matchingKeys.size.toUInt()) {
        dataStore.requestNextValues(updateListener.request, updateListener.matchingKeys)?.also {
            // Always at the end so no need to order
            updateListener.addValuesAtEnd(it.key, it.values)
            sendChannel.send(it)
        }
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
    changeProcessor: ((IsChange) -> Unit)?
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

