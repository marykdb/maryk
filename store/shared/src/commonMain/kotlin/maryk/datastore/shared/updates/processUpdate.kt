package maryk.datastore.shared.updates

import kotlinx.coroutines.channels.SendChannel
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.Key
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.requests.IsChangesRequest
import maryk.core.query.requests.IsFetchRequest
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
internal suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> Update<DM, P>.process(
    request: IsFetchRequest<DM, P, *>,
    currentKeys: MutableList<Key<DM>>,
    dataStore: IsDataStore,
    sendChannel: SendChannel<IsUpdateResponse<DM, P>>
) {
    if (request !is IsChangesRequest<*, *, *> || request.fromVersion <= version.timestamp) {
        when (this) {
            is Addition<DM, P> -> {
                if (values.matches(request.where)) {
                    if (request !is IsScanRequest<*, *, *> || request.limit > currentKeys.size.toUInt()) {
                        // TODO ORDER
                        currentKeys += key

                        sendChannel.send(
                            AdditionUpdate(
                                key = key,
                                version = version.timestamp,
                                values = values.filterWithSelect(request.select)
                            )
                        )
                    }
                }
            }
            is Change<DM, P> -> {
                var shouldDelete = false
                val filteredChanges = changes.filterWithSelect(request.select) {
                    if (it is ObjectSoftDeleteChange && it.isDeleted) {
                        shouldDelete = true
                    }
                }

                if (currentKeys.contains(key)) {
                    if (shouldDelete) {
                        handleDeletion(dataStore, request, this, currentKeys, sendChannel)
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
                    handleDeletion(dataStore, request, this, currentKeys, sendChannel)
                }
            }
        }
    }
}

/** Handles the deletion of Values defined in [change] and if necessary request a new value to put at end */
private suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> handleDeletion(
    dataStore: IsDataStore,
    request: IsFetchRequest<DM, P, *>,
    change: Update<DM, P>,
    currentKeys: MutableList<Key<DM>>,
    sendChannel: SendChannel<IsUpdateResponse<DM, P>>
) {
    currentKeys -= change.key

    sendChannel.send(
        RemovalUpdate(
            key = change.key,
            version = change.version.timestamp
        )
    )

    if (request is IsScanRequest<DM, P, *> && request.limit - 1u == currentKeys.size.toUInt()) {
        dataStore.requestNextValues(request, currentKeys.last())?.also {
            // Always at the end so no need to order
            currentKeys += it.key
            sendChannel.send(it)
        }
    }
}

/** Requests next values object after [lastKey] */
private suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> IsDataStore.requestNextValues(
    request: IsScanRequest<DM, P, *>,
    lastKey: Key<DM>
): AdditionUpdate<DM, P>? {
    val nextResults = execute(
        request.dataModel.scan(
            lastKey,
            request.select,
            where = request.where,
            order = request.order,
            limit = 2u,
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

