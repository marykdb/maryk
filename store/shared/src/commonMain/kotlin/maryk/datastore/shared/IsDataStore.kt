package maryk.datastore.shared

import kotlinx.coroutines.flow.Flow
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.requests.IsFetchRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.responses.IsDataResponse
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.ProcessResponse
import maryk.core.services.responses.UpdateResponse

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
    suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions, RQ: IsFetchRequest<DM, P, RP>, RP: IsDataResponse<DM, P>> executeFlow(
        request: RQ
    ): Flow<IsUpdateResponse<DM, P>>

    /** Processes an update response to sync its results in this data store */
    suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions, UR : IsUpdateResponse<DM, P>> processUpdate(
        updateResponse: UpdateResponse<DM, P>
    ): ProcessResponse

    /** Close the data store */
    fun close()

    /** Close all open listeners */
    suspend fun closeAllListeners()
}
