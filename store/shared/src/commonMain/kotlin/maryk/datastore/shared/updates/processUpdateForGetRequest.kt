package maryk.datastore.shared.updates

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.requests.IsGetRequest
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.DeletionUpdate
import maryk.datastore.shared.updates.Update.Addition
import maryk.datastore.shared.updates.Update.Change
import maryk.datastore.shared.updates.Update.Deletion

internal suspend fun <DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> Update<DM, P>.processGetRequest(
    request: IsGetRequest<*, *, *>,
    updateListener: UpdateListener<*, *>
) {
    if (request.keys.contains(key)) {
        val update = when (this) {
            is Addition<DM, P> -> {
                AdditionUpdate(
                    dataModel = dataModel,
                    key = key,
                    version = version.timestamp,
                    values = values
                )
            }
            is Change<DM, P> -> {
                ChangeUpdate(
                    dataModel = dataModel,
                    key = key,
                    version = version.timestamp,
                    changes = changes
                )
            }
            is Deletion<DM, P> -> {
                if (isHardDelete || request.filterSoftDeleted) {
                    DeletionUpdate(
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
