package maryk.core.query.requests

import maryk.core.aggregations.Aggregations
import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.IsRootDataModel
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.QueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.boolean
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.definitions.embedObject
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.number
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.Key
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.RequestContext
import maryk.core.query.filters.IsFilter
import maryk.core.query.orders.IsOrder
import maryk.core.query.requests.RequestType.ScanUpdates
import maryk.core.query.responses.UpdatesResponse
import maryk.core.values.ObjectValues

/**
 * Creates a request to scan DataObjects by key from [startKey] until [limit]
 * It will only fetch the updates [fromVersion] (Inclusive) until [maxVersions] (Default=1) is reached.
 * Can also contain a [where] filter, [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order]
 */
fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> DM.scanUpdates(
    startKey: Key<DM>? = null,
    where: IsFilter? = null,
    order: IsOrder? = null,
    limit: UInt = 100u,
    includeStart: Boolean = true,
    fromVersion: ULong = 0uL,
    toVersion: ULong? = null,
    maxVersions: UInt = 1u,
    select: RootPropRefGraph<P>? = null,
    filterSoftDeleted: Boolean = true,
    orderedKeys: List<Key<DM>>? = null
) =
    ScanUpdatesRequest(
        this,
        startKey,
        where,
        order,
        limit,
        includeStart,
        fromVersion,
        toVersion,
        maxVersions,
        select,
        filterSoftDeleted,
        orderedKeys
    )

/**
 * A Request to scan DataObjects by key from [startKey] until [limit] for specific [dataModel]
 * It will only fetch the updates [fromVersion] (Inclusive) until [maxVersions] (Default=1) is reached.
 * Can also contain a [where] filter, [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order] and only selected properties can be returned with a [select] graph
 */
data class ScanUpdatesRequest<DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> internal constructor(
    override val dataModel: DM,
    override val startKey: Key<DM>? = null,
    override val where: IsFilter? = null,
    override val order: IsOrder? = null,
    override val limit: UInt = 100u,
    override val includeStart: Boolean = true,
    override val fromVersion: ULong = 0uL,
    override val toVersion: ULong? = null,
    override val maxVersions: UInt = 1u,
    override val select: RootPropRefGraph<P>? = null,
    override val filterSoftDeleted: Boolean = true,
    val orderedKeys: List<Key<DM>>? = null
) : IsScanRequest<DM, P, UpdatesResponse<DM, P>>, IsUpdatesRequest<DM, P, UpdatesResponse<DM, P>>, IsTransportableRequest<UpdatesResponse<DM, P>> {
    override val requestType = ScanUpdates
    override val responseModel = UpdatesResponse

    // Aggregations are not allowed on a scan changes request
    override val aggregations: Aggregations? = null

    @Suppress("unused")
    object Properties : ObjectPropertyDefinitions<ScanUpdatesRequest<*, *>>() {
        val from by addDataModel(ScanUpdatesRequest<*, *>::dataModel)
        val startKey by addStartKey(ScanUpdatesRequest<*, *>::startKey)
        val select by embedObject(3u, ScanUpdatesRequest<*, *>::select, dataModel = { RootPropRefGraph })
        val where by addFilter(ScanUpdatesRequest<*, *>::where)
        val toVersion by number(5u, ScanUpdatesRequest<*, *>::toVersion, UInt64, required = false)
        val filterSoftDeleted  by boolean(6u, ScanUpdatesRequest<*, *>::filterSoftDeleted, default = true)
        val order by addOrder(ScanUpdatesRequest<*, *>::order)
        val limit by number(9u, ScanUpdatesRequest<*, *>::limit, type = UInt32, default = 100u)
        val includeStart by boolean(10u, ScanUpdatesRequest<*, *>::includeStart, default = true)
        val fromVersion by number(11u, ScanUpdatesRequest<*, *>::fromVersion, UInt64)
        val maxVersions by number(12u, ScanUpdatesRequest<*, *>::maxVersions, UInt32, maxValue = 1u)
        val orderedKeys by list(
            index = 13u, getter = ScanUpdatesRequest<*, *>::orderedKeys,
            valueDefinition = ContextualReferenceDefinition<RequestContext>(
                contextualResolver = {
                    it?.dataModel as IsRootDataModel<*>? ?: throw ContextNotFoundException()
                }
            )
        )
    }

    companion object : QueryDataModel<ScanUpdatesRequest<*, *>, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<ScanUpdatesRequest<*, *>, Properties>) =
            ScanUpdatesRequest<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>(
                dataModel = values(1u),
                startKey = values(2u),
                select = values(3u),
                where = values(4u),
                toVersion = values(5u),
                filterSoftDeleted = values(6u),
                order = values(8u),
                limit = values(9u),
                includeStart = values(10u),
                fromVersion = values(11u),
                maxVersions = values(12u),
                orderedKeys = values(13u)
            )
    }
}
