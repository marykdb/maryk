package maryk.datastore.remote

import maryk.core.models.IsObjectDataModel
import maryk.core.models.TypedObjectDataModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.list
import maryk.core.query.RequestContext
import maryk.core.query.requests.MAX_ATOMIC_OPERATIONS
import maryk.core.query.responses.UpdateResponse
import maryk.core.values.ObjectValues

/** Previous envelope retained only to verify the public wire format. */
internal data class LegacyRestoreVersion(val updates: List<UpdateResponse<*>>) {
    companion object : TypedObjectDataModel<LegacyRestoreVersion, IsObjectDataModel<LegacyRestoreVersion>, RequestContext, RequestContext>() {
        val updates by list(1u, LegacyRestoreVersion::updates, maxSize = MAX_ATOMIC_OPERATIONS,
            valueDefinition = EmbeddedObjectDefinition(dataModel = { UpdateResponse }))
        override fun invoke(values: ObjectValues<LegacyRestoreVersion, IsObjectDataModel<LegacyRestoreVersion>>) =
            LegacyRestoreVersion(values(updates.index))
    }
}
