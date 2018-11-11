package maryk.core.processors.datastore

import maryk.core.models.RootDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.responses.IsResponse

/** Processes actions on data stores. */
interface IsDataStore {
    /** Execute a single store [request] and retrieve response */
    suspend fun <DM: RootDataModel<DM, P>, P: PropertyDefinitions, RQ: IsStoreRequest<DM, RP>, RP: IsResponse> execute(
        request: RQ
    ): RP
}
