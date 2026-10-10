package maryk.core.query.requests

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.QueryModel
import maryk.core.properties.definitions.boolean
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.definitions.embedObject
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.Key
import maryk.core.properties.types.validateKey
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.RequestContext
import maryk.core.query.filters.IsFilter
import maryk.core.query.responses.UpdatesResponse
import maryk.core.values.ObjectValues

/** Gets up to [limit] historical update entries for [key], newest-first. */
fun <DM : IsRootDataModel> DM.getUpdateHistory(
    key: Key<DM>,
    where: IsFilter? = null,
    limit: UInt = 100u,
    fromVersion: ULong = 0uL,
    toVersion: ULong? = null,
    select: RootPropRefGraph<DM>? = null,
    filterSoftDeleted: Boolean = true,
    cursor: UpdateHistoryCursor? = null,
) = GetUpdateHistoryRequest(this, key, where, limit, fromVersion, toVersion, select, filterSoftDeleted, cursor)

data class GetUpdateHistoryRequest<DM : IsRootDataModel>(
    override val dataModel: DM,
    val key: Key<DM>,
    override val where: IsFilter? = null,
    override val limit: UInt = 100u,
    override val fromVersion: ULong = 0uL,
    override val toVersion: ULong? = null,
    override val select: RootPropRefGraph<DM>? = null,
    override val filterSoftDeleted: Boolean = true,
    override val cursor: UpdateHistoryCursor? = null,
) : IsUpdateHistoryRequest<DM>, IsTransportableRequest<UpdatesResponse<DM>> {
    override val historyKey get() = key
    override val maxVersions = 1u
    override val requestType = RequestType.GetUpdateHistory
    override val responseModel = UpdatesResponse
    override val aggregations = null

    init {
        dataModel.validateKey(key)
        if (limit == 0u || limit > MAX_SCAN_LIMIT) {
            throw RequestException("Get update history limit must be between 1 and $MAX_SCAN_LIMIT")
        }
        if (toVersion != null && fromVersion > toVersion) {
            throw RequestException("Get update history fromVersion cannot exceed toVersion")
        }
    }

    companion object : QueryModel<GetUpdateHistoryRequest<*>, Companion>() {
        val from by addDataModel { it.dataModel }
        val key by contextual(
            index = 2u,
            getter = GetUpdateHistoryRequest<*>::key,
            definition = ContextualReferenceDefinition<RequestContext>(
                contextualResolver = { it?.dataModel as? IsRootDataModel ?: throw ContextNotFoundException() }
            )
        )
        val select by embedObject(3u, GetUpdateHistoryRequest<*>::select, dataModel = { RootPropRefGraph })
        val where by addFilter(GetUpdateHistoryRequest<*>::where)
        val toVersion by number(5u, GetUpdateHistoryRequest<*>::toVersion, UInt64, required = false)
        val filterSoftDeleted by boolean(6u, GetUpdateHistoryRequest<*>::filterSoftDeleted, default = true)
        val limit by number(9u, GetUpdateHistoryRequest<*>::limit, UInt32, minValue = 1u, maxValue = MAX_SCAN_LIMIT, default = 100u)
        val fromVersion by number(11u, GetUpdateHistoryRequest<*>::fromVersion, UInt64)
        val cursor by embedObject(12u, GetUpdateHistoryRequest<*>::cursor, dataModel = { UpdateHistoryCursor.Model })

        override fun invoke(values: ObjectValues<GetUpdateHistoryRequest<*>, Companion>) = GetUpdateHistoryRequest(
            dataModel = values(from.index), key = values(key.index), where = values(where.index),
            limit = values(limit.index), fromVersion = values(fromVersion.index), toVersion = values(toVersion.index),
            select = values(select.index), filterSoftDeleted = values(filterSoftDeleted.index), cursor = values(cursor.index),
        )
    }
}
