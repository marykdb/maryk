package io.maryk.cli.commands

import kotlinx.coroutines.flow.Flow
import maryk.core.models.IsRootDataModel
import maryk.core.query.requests.IsFetchRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.responses.IsDataResponse
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.ProcessResponse
import maryk.datastore.shared.IsDataStore

open class FakeDataStore(
    override val dataModelsById: Map<UInt, IsRootDataModel> = emptyMap(),
    override val keepAllVersions: Boolean = true,
) : IsDataStore {
    override val dataModelIdsByString: Map<String, UInt> = dataModelsById.map { (id, model) ->
        model.Meta.name to id
    }.toMap()

    override val supportsFuzzyQualifierFiltering: Boolean = false
    override val supportsSubReferenceFiltering: Boolean = false

    var closed: Boolean = false
    var listenersClosed: Boolean = false

    override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
        request: RQ,
    ): RP = throw NotImplementedError("Not used in tests")

    override suspend fun <DM : IsRootDataModel, RQ : IsFetchRequest<DM, RP>, RP : IsDataResponse<DM>> executeFlow(
        request: RQ,
    ): Flow<IsUpdateResponse<DM>> = throw NotImplementedError("Not used in tests")

    override suspend fun <DM : IsRootDataModel> processUpdate(
        updateResponse: UpdateResponse<DM>,
    ): ProcessResponse<DM> = throw NotImplementedError("Not used in tests")

    override suspend fun close() {
        closed = true
    }

    override suspend fun closeAllListeners() {
        listenersClosed = true
    }
}
