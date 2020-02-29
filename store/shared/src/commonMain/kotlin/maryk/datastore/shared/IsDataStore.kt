package maryk.datastore.shared

import kotlinx.coroutines.flow.Flow
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.requests.IsChangesRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.responses.IsResponse
import maryk.datastore.shared.updates.Update

/** Processes actions on data stores. */
interface IsDataStore {
    val dataModelsById: Map<UInt, RootDataModel<*, *>>
    val dataModelIdsByString: Map<String, UInt>
    val keepAllVersions: Boolean

    /** Execute a single store [request] and retrieve response */
    suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
        request: RQ
    ): RP

    /** Execute a single store [request] and retrieve a flow of responses */
    fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions, RQ, RP : IsResponse> executeFlow(
        request: RQ
    ): Flow<Update<DM>> where RQ : IsStoreRequest<DM, RP>, RQ: IsChangesRequest<DM, P, RP>

    /** Close the data store */
    fun close()
}
