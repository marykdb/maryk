package maryk.core.query.requests

import maryk.core.aggregations.Aggregations
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.QueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.Key
import maryk.core.query.filters.IsFilter
import maryk.core.query.orders.IsOrder
import maryk.core.query.requests.RequestType.ScanChanges
import maryk.core.query.responses.ChangesResponse
import maryk.core.values.ObjectValues

/**
 * Creates a request to scan DataObjects by key from [startKey] until [limit]
 * It will only fetch the changes [fromVersion] (Inclusive) until [maxVersions] (Default=1000) is reached.
 * Can also contain a [where] filter, [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order]
 */
fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> DM.scanChanges(
    startKey: Key<DM>? = null,
    where: IsFilter? = null,
    order: IsOrder? = null,
    limit: UInt = 100u,
    fromVersion: ULong = 0uL,
    toVersion: ULong? = null,
    maxVersions: UInt = 1u,
    select: RootPropRefGraph<P>? = null,
    filterSoftDeleted: Boolean = true,
    aggregations: Aggregations? = null
) =
    ScanChangesRequest(
        this,
        startKey,
        where,
        order,
        limit,
        fromVersion,
        toVersion,
        maxVersions,
        select,
        filterSoftDeleted,
        aggregations
    )

/**
 * A Request to scan DataObjects by key from [startKey] until [limit] for specific [dataModel]
 * It will only fetch the changes [fromVersion] (Inclusive) until [maxVersions] (Default=1000) is reached.
 * Can also contain a [where] filter, [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order] and only selected properties can be returned with a [select] graph
 */
data class ScanChangesRequest<DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> internal constructor(
    override val dataModel: DM,
    override val startKey: Key<DM>? = null,
    override val where: IsFilter? = null,
    override val order: IsOrder? = null,
    override val limit: UInt = 100u,
    override val fromVersion: ULong = 0uL,
    override val toVersion: ULong? = null,
    override val maxVersions: UInt = 1u,
    override val select: RootPropRefGraph<P>? = null,
    override val filterSoftDeleted: Boolean = true,
    override val aggregations: Aggregations? = null
) : IsScanRequest<DM, P, ChangesResponse<DM>>, IsChangesRequest<DM, P, ChangesResponse<DM>> {
    override val requestType = ScanChanges
    override val responseModel = ChangesResponse

    @Suppress("unused")
    object Properties : ObjectPropertyDefinitions<ScanChangesRequest<*, *>>() {
        val dataModel = IsObjectRequest.addDataModel("from", this, ScanChangesRequest<*, *>::dataModel)
        val startKey = IsScanRequest.addStartKey(this, ScanChangesRequest<*, *>::startKey)
        val select = IsFetchRequest.addSelect(this, ScanChangesRequest<*, *>::select)
        val where = IsFetchRequest.addFilter(this, ScanChangesRequest<*, *>::where)
        val toVersion = IsFetchRequest.addToVersion(this, ScanChangesRequest<*, *>::toVersion)
        val filterSoftDeleted = IsFetchRequest.addFilterSoftDeleted(this, ScanChangesRequest<*, *>::filterSoftDeleted)
        val aggregations = IsFetchRequest.addAggregationsDefinition(this, ScanChangesRequest<*, *>::aggregations)
        val order = IsScanRequest.addOrder(this, ScanChangesRequest<*, *>::order)
        val limit = IsScanRequest.addLimit(this, ScanChangesRequest<*, *>::limit)
        val fromVersion = IsChangesRequest.addFromVersion(10u, this, ScanChangesRequest<*, *>::fromVersion)
        val maxVersions = IsChangesRequest.addMaxVersions(11u, this, ScanChangesRequest<*, *>::maxVersions)
    }

    companion object : QueryDataModel<ScanChangesRequest<*, *>, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<ScanChangesRequest<*, *>, Properties>) =
            ScanChangesRequest<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>(
                dataModel = values(1u),
                startKey = values(2u),
                select = values(3u),
                where = values(4u),
                toVersion = values(5u),
                filterSoftDeleted = values(6u),
                aggregations = values(7u),
                order = values(8u),
                limit = values(9u),
                fromVersion = values(10u),
                maxVersions = values(11u)
            )
    }
}
