package maryk.core.query.requests

import maryk.core.models.IsRootDataModel
import maryk.core.properties.types.Key
import maryk.core.query.responses.UpdatesResponse

/** Finite, newest-first history entries. This is not a live query. */
interface IsUpdateHistoryRequest<DM : IsRootDataModel> : IsUpdatesRequest<DM, UpdatesResponse<DM>> {
    val limit: UInt
    val cursor: UpdateHistoryCursor?
    val historyKey: Key<DM>?
        get() = null
}
