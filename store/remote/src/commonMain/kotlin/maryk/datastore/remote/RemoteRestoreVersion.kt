package maryk.datastore.remote

import maryk.core.models.IsObjectDataModel
import maryk.core.models.TypedObjectDataModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.list
import maryk.core.query.RequestContext
import maryk.core.query.requests.MAX_ATOMIC_OPERATIONS
import maryk.core.query.responses.UpdateResponse
import maryk.core.values.ObjectValues

internal data class RemoteRestoreVersion(val updates: List<UpdateResponse<*>>) {
    companion object : TypedObjectDataModel<RemoteRestoreVersion, IsObjectDataModel<RemoteRestoreVersion>, RequestContext, RequestContext>() {
        val updates by list(1u, RemoteRestoreVersion::updates, maxSize = MAX_ATOMIC_OPERATIONS,
            valueDefinition = EmbeddedObjectDefinition(dataModel = { UpdateResponse }))
        override fun invoke(values: ObjectValues<RemoteRestoreVersion, IsObjectDataModel<RemoteRestoreVersion>>) =
            RemoteRestoreVersion(values(updates.index))
    }
}
