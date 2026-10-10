package maryk.datastore.shared

import maryk.core.models.IsRootDataModel
import maryk.core.exceptions.RequestException
import maryk.core.properties.types.Key
import maryk.core.query.requests.IsUpdateHistoryRequest
import maryk.core.query.requests.createHistoryCursor
import maryk.core.query.requests.resolveHistoryCursor
import maryk.core.query.responses.FetchByUpdateHistoryIndex
import maryk.core.query.responses.UpdatesResponse
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.lib.extensions.compare.compareTo

/** Shared query binding, ordering and matching-entry lookahead for history processors. */
class UpdateHistoryPaging<DM : IsRootDataModel>(private val request: IsUpdateHistoryRequest<DM>) {
    val continuation = request.resolveHistoryCursor()
    val scanLimit = request.limit + 1u
    val toVersion = continuation?.upperVersion ?: request.toVersion
    private var upperVersion = continuation?.upperVersion

    /** Called in descending version, ascending key order, before fetching values. */
    fun accepts(version: ULong, key: Key<DM>): Boolean {
        if (toVersion != null && version > toVersion) return false
        if (upperVersion == null) upperVersion = version
        if (version > upperVersion!! || version < request.fromVersion) return false
        request.historyKey?.let { if (it != key) return false }
        continuation?.let {
            if (version > it.version || (version == it.version && (key.bytes compareTo it.key.bytes) <= 0)) return false
        }
        return true
    }

    fun response(updates: List<IsUpdateResponse<DM>>): UpdatesResponse<DM> {
        val kept = updates.take(request.limit.toInt())
        val nextCursor = if (updates.size.toUInt() > request.limit) {
            val last = kept.last()
            val key = when (last) {
                is AdditionUpdate<DM> -> last.key
                is ChangeUpdate<DM> -> last.key
                is RemovalUpdate<DM> -> last.key
                else -> throw RequestException("Unexpected update in history page")
            }
            request.createHistoryCursor(upperVersion!!, last.version, key)
        } else null
        return UpdatesResponse(request.dataModel, kept, FetchByUpdateHistoryIndex(), nextCursor)
    }
}
