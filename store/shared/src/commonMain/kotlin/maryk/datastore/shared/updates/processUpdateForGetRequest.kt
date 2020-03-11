package maryk.datastore.shared.updates

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.requests.IsChangesRequest
import maryk.core.query.requests.IsGetRequest
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.datastore.shared.updates.Update.Addition
import maryk.datastore.shared.updates.Update.Change
import maryk.datastore.shared.updates.Update.Deletion

internal suspend fun <DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> Update<DM, P>.processGetRequest(
    request: IsGetRequest<*, *, *>,
    updateListener: UpdateListener<*, *>
) {
    if (request.keys.contains(key)) {
        if (request !is IsChangesRequest<*, *, *> || request.fromVersion <= version.timestamp) {
            val update = when (this) {
                is Addition<DM, P> -> {
                    if (request is IsChangesRequest<*, *, *> && request.fromVersion > version.timestamp) {
                        null // Record is of before the fromVersion time
                    } else if (values.matches(request.where)) {
                        AdditionUpdate(
                            dataModel = dataModel,
                            key = key,
                            version = version.timestamp,
                            values = values.filterWithSelect(request.select)
                        )
                    } else null
                }
                is Change<DM, P> -> {
                    var shouldRemove = false
                    val filteredChanges = changes.filterWithSelect(request.select) {
                        if (it is ObjectSoftDeleteChange && it.isDeleted) {
                            shouldRemove = true
                        }
                    }

                    if (shouldRemove) {
                        RemovalUpdate(
                            dataModel = dataModel,
                            key = key,
                            version = version.timestamp
                        )
                    } else {
                        ChangeUpdate(
                            dataModel = dataModel,
                            key = key,
                            version = version.timestamp,
                            changes = filteredChanges
                        )
                    }
                }
                is Deletion<DM, P> -> {
                    if (isHardDelete || request.filterSoftDeleted) {
                        RemovalUpdate(
                            dataModel = dataModel,
                            key = key,
                            version = version.timestamp
                        )
                    } else null
                }
            }

            if (update != null) {
                @Suppress("UNCHECKED_CAST")
                (updateListener as UpdateListener<DM, P>).sendChannel.send(update)
            }
        }
    }
}

private fun List<IsChange>.filterWithSelect(
    select: RootPropRefGraph<out PropertyDefinitions>?,
    changeProcessor: ((IsChange) -> Unit)?
): List<IsChange> {
    if (select == null) {
        // process all changes
        changeProcessor?.let { this.forEach(it) }
        return this
    }
    return this.mapNotNull { change: IsChange ->
        changeProcessor?.invoke(change)
        change.filterWithSelect(select)
    }
}
