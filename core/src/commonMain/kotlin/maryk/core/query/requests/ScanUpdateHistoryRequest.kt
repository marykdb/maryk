package maryk.core.query.requests

import maryk.core.aggregations.Aggregations
import maryk.core.models.IsRootDataModel
import maryk.core.models.QueryModel
import maryk.core.properties.definitions.boolean
import maryk.core.properties.definitions.embedObject
import maryk.core.properties.definitions.number
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.filters.IsFilter
import maryk.core.query.requests.RequestType.ScanUpdateHistory
import maryk.core.query.responses.UpdatesResponse
import maryk.core.values.ObjectValues

/**
 * Creates a request to scan update entries by version using the engine update history index.
 * Results are newest-first and can repeat keys.
 */
fun <DM : IsRootDataModel> DM.scanUpdateHistory(
    where: IsFilter? = null,
    limit: UInt = 100u,
    fromVersion: ULong = 0uL,
    toVersion: ULong? = null,
    select: RootPropRefGraph<DM>? = null,
    filterSoftDeleted: Boolean = true
) =
    ScanUpdateHistoryRequest(
        dataModel = this,
        where = where,
        limit = limit,
        fromVersion = fromVersion,
        toVersion = toVersion,
        select = select,
        filterSoftDeleted = filterSoftDeleted
    )

/**
 * A request to scan update entries by version using the engine update history index.
 * Results are newest-first and can repeat keys.
 */
data class ScanUpdateHistoryRequest<DM : IsRootDataModel> internal constructor(
    override val dataModel: DM,
    override val where: IsFilter? = null,
    val limit: UInt = 100u,
    override val fromVersion: ULong = 0uL,
    override val toVersion: ULong? = null,
    override val select: RootPropRefGraph<DM>? = null,
    override val filterSoftDeleted: Boolean = true
) : IsFetchRequest<DM, UpdatesResponse<DM>>, IsUpdatesRequest<DM, UpdatesResponse<DM>>, IsTransportableRequest<UpdatesResponse<DM>> {
    override val maxVersions: UInt = 1u
    override val requestType = ScanUpdateHistory
    override val responseModel = UpdatesResponse
    override val aggregations: Aggregations? = null

    companion object : QueryModel<ScanUpdateHistoryRequest<*>, Companion>() {
        val from by addDataModel { it.dataModel }
        val select by embedObject(3u, ScanUpdateHistoryRequest<*>::select, dataModel = { RootPropRefGraph })
        val where by addFilter(ScanUpdateHistoryRequest<*>::where)
        val toVersion by number(5u, ScanUpdateHistoryRequest<*>::toVersion, UInt64, required = false)
        val filterSoftDeleted by boolean(6u, ScanUpdateHistoryRequest<*>::filterSoftDeleted, default = true)
        val limit by number(9u, ScanUpdateHistoryRequest<*>::limit, type = UInt32, default = 100u)
        val fromVersion by number(11u, ScanUpdateHistoryRequest<*>::fromVersion, UInt64)

        override fun invoke(values: ObjectValues<ScanUpdateHistoryRequest<*>, Companion>) =
            ScanUpdateHistoryRequest(
                dataModel = values(from.index),
                select = values(select.index),
                where = values(where.index),
                toVersion = values(toVersion.index),
                filterSoftDeleted = values(filterSoftDeleted.index),
                limit = values(limit.index),
                fromVersion = values(fromVersion.index)
            )
    }
}
