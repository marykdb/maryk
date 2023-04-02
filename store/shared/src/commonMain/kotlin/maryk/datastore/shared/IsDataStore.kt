package maryk.datastore.shared

import kotlinx.coroutines.flow.Flow
import maryk.core.models.definitions.RootDataModelDefinition
import maryk.core.models.IsRootDataModel
import maryk.core.query.requests.IsFetchRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.responses.IsDataResponse
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.ProcessResponse
import maryk.core.query.responses.UpdateResponse

/** Processes actions on data stores. */
interface IsDataStore {
    val dataModelsById: Map<UInt, RootDataModelDefinition<*>>
    val dataModelIdsByString: Map<String, UInt>
    val keepAllVersions: Boolean

    /** Execute a single store [request] and retrieve response */
    suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
        request: RQ
    ): RP

    /** Execute a single store [request] and retrieve a flow of responses */
    suspend fun <DM : IsRootDataModel, RQ: IsFetchRequest<DM, RP>, RP: IsDataResponse<DM>> executeFlow(
        request: RQ
    ): Flow<IsUpdateResponse<DM>>

    /** Processes an update response to sync its results in this data store */
    suspend fun <DM : IsRootDataModel> processUpdate(
        updateResponse: UpdateResponse<DM>
    ): ProcessResponse<DM>

    /** Close the data store */
    fun close()

    /** Close all open listeners */
    suspend fun closeAllListeners()
}
