package maryk.datastore.shared

import kotlinx.coroutines.flow.Flow
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.types.Key
import maryk.core.query.requests.GetUpdatesRequest
import maryk.core.query.requests.IsChangesRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.requests.IsUpdatesRequest
import maryk.core.query.responses.ChangesResponse
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.UpdatesResponse
import maryk.core.query.responses.updates.IsUpdateResponse

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
    suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions, RQ> executeFlow(
        request: RQ
    ): Flow<IsUpdateResponse<DM, P>> where RQ : IsStoreRequest<DM, UpdatesResponse<DM, P>>, RQ: IsUpdatesRequest<DM, P, UpdatesResponse<DM, P>>

    /** Close the data store */
    fun close()

    /** Close all open listeners */
    fun closeAllListeners()
}
