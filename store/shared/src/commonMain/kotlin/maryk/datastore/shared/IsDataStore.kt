package maryk.datastore.shared

import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.responses.IsResponse

/** Processes actions on data stores. */
interface IsDataStore {
    val dataModelsById: Map<UInt, RootDataModel<*, *>>
    val dataModelIdsByString: Map<String, UInt>
    val keepAllVersions: Boolean

    /** Execute a single store [request] and retrieve response */
    suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
        request: RQ
    ): RP

    /** Close the data store */
    fun close()
}
