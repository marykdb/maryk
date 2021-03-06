package maryk.datastore.memory.processors

import kotlinx.coroutines.channels.SendChannel
import maryk.core.clock.HLC
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.requests.DeleteRequest
import maryk.core.query.responses.DeleteResponse
import maryk.core.query.responses.statuses.IsDeleteResponseStatus
import maryk.core.query.responses.statuses.ServerFail
import maryk.datastore.memory.IsStoreFetcher
import maryk.datastore.memory.records.DataStore
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.updates.Update
import kotlin.native.concurrent.SharedImmutable

internal typealias DeleteStoreAction<DM, P> = StoreAction<DM, P, DeleteRequest<DM>, DeleteResponse<DM>>
internal typealias AnyDeleteStoreAction = DeleteStoreAction<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

@SharedImmutable
internal val objectSoftDeleteQualifier = byteArrayOf(0)

/** Processes a DeleteRequest in a [storeAction] into a data store from [dataStoreFetcher] */
internal suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processDeleteRequest(
    version: HLC,
    storeAction: DeleteStoreAction<DM, P>,
    dataStoreFetcher: IsStoreFetcher<*, *>,
    updateSendChannel: SendChannel<Update<DM, P>>
) {
    val deleteRequest = storeAction.request
    val statuses = mutableListOf<IsDeleteResponseStatus<DM>>()

    if (deleteRequest.keys.isNotEmpty()) {
        @Suppress("UNCHECKED_CAST")
        val dataStore = dataStoreFetcher(deleteRequest.dataModel) as DataStore<DM, P>

        // Delete it from history if it is a hard deletion
        val historicStoreIndexValuesWalker = if (deleteRequest.hardDelete && dataStore.keepAllVersions) {
            HistoricStoreIndexValuesWalker
        } else null

        for (key in deleteRequest.keys) {
            try {
                statuses.add(
                    processDelete(
                        dataStore,
                        deleteRequest.dataModel,
                        key,
                        version,
                        deleteRequest.hardDelete,
                        historicStoreIndexValuesWalker,
                        updateSendChannel
                    )
                )
            } catch (e: Throwable) {
                statuses.add(ServerFail(e.toString(), e))
            }
        }
    }

    storeAction.response.complete(
        DeleteResponse(
            storeAction.request.dataModel,
            statuses
        )
    )
}
