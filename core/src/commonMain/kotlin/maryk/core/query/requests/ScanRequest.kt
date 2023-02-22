package maryk.core.query.requests

import maryk.core.aggregations.Aggregations
import maryk.core.models.IsRootDataModel
import maryk.core.models.QueryDataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.RootModel
import maryk.core.properties.definitions.boolean
import maryk.core.properties.definitions.embedObject
import maryk.core.properties.definitions.number
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.Key
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.filters.IsFilter
import maryk.core.query.orders.IsOrder
import maryk.core.query.requests.RequestType.Scan
import maryk.core.query.responses.ValuesResponse
import maryk.core.values.ObjectValues

/**
 * Creates a Request to scan DataObjects by key from [startKey] until [limit] and only return [select]
 * values of properties.
 * Can also contain a [where] filter, [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order]
 */
fun <DM : IsRootDataModel<P>, P : IsValuesPropertyDefinitions> DM.scan(
    startKey: Key<DM>? = null,
    select: RootPropRefGraph<P>? = null,
    where: IsFilter? = null,
    order: IsOrder? = null,
    limit: UInt = 100u,
    includeStart: Boolean = true,
    toVersion: ULong? = null,
    filterSoftDeleted: Boolean = true,
    aggregations: Aggregations? = null
) =
    ScanRequest(this, startKey, select, where, order, limit, includeStart, toVersion, filterSoftDeleted, aggregations)

/**
 * Creates a Request to scan DataObjects by key from [startKey] until [limit] and only return [select]
 * values of properties.
 * Can also contain a [where] filter, [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order]
 */
fun <DM : RootModel<P>, P : IsValuesPropertyDefinitions> DM.scan(
    startKey: Key<RootDataModel<P>>? = null,
    select: RootPropRefGraph<P>? = null,
    where: IsFilter? = null,
    order: IsOrder? = null,
    limit: UInt = 100u,
    includeStart: Boolean = true,
    toVersion: ULong? = null,
    filterSoftDeleted: Boolean = true,
    aggregations: Aggregations? = null
) =
    ScanRequest(this.Model, startKey, select, where, order, limit, includeStart, toVersion, filterSoftDeleted, aggregations)


/**
 * A Request to scan DataObjects by key from [startKey] until [limit]
 * for specific [dataModel] and only return [select]
 * values of properties.
 * Can also contain a [where] filter, [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order]
 */
data class ScanRequest<DM : IsRootDataModel<P>, P : IsValuesPropertyDefinitions> internal constructor(
    override val dataModel: DM,
    override val startKey: Key<DM>? = null,
    override val select: RootPropRefGraph<P>? = null,
    override val where: IsFilter? = null,
    override val order: IsOrder? = null,
    override val limit: UInt = 100u,
    override val includeStart: Boolean = true,
    override val toVersion: ULong? = null,
    override val filterSoftDeleted: Boolean = true,
    override val aggregations: Aggregations? = null
) : IsScanRequest<DM, P, ValuesResponse<DM, P>>, IsTransportableRequest<ValuesResponse<DM, P>> {
    override val requestType = Scan
    override val responseModel = ValuesResponse

    object Properties : ObjectPropertyDefinitions<ScanRequest<*, *>>() {
        val from by addDataModel(ScanRequest<*, *>::dataModel)
        val startKey by addStartKey(ScanRequest<*, *>::startKey)
        val select by embedObject(3u, ScanRequest<*, *>::select, dataModel = { RootPropRefGraph })
        val where by addFilter(ScanRequest<*, *>::where)
        val toVersion by number(5u, ScanRequest<*, *>::toVersion, UInt64, required = false)
        val filterSoftDeleted  by boolean(6u, ScanRequest<*, *>::filterSoftDeleted, default = true)
        val aggregations by embedObject(7u, ScanRequest<*, *>::aggregations, dataModel = { Aggregations }, alternativeNames = setOf("aggs"))
        val order by addOrder(ScanRequest<*, *>::order)
        val limit by number(9u, ScanRequest<*, *>::limit, type = UInt32, default = 100u)
        val includeStart by boolean(10u, ScanRequest<*, *>::includeStart, default = true)
    }

    companion object : QueryDataModel<ScanRequest<*, *>, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<ScanRequest<*, *>, Properties>) = ScanRequest(
            dataModel = values<IsRootDataModel<IsValuesPropertyDefinitions>>(1u),
            startKey = values(2u),
            select = values(3u),
            where = values(4u),
            toVersion = values(5u),
            filterSoftDeleted = values(6u),
            aggregations = values(7u),
            order = values(8u),
            limit = values(9u),
            includeStart = values(10u)
        )
    }
}
