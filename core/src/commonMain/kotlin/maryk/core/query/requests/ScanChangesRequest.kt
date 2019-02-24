package maryk.core.query.requests

import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.QueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.Key
import maryk.core.query.filters.IsFilter
import maryk.core.query.orders.IsOrder
import maryk.core.query.responses.ChangesResponse
import maryk.core.values.ObjectValues

/**
 * Creates a request to scan DataObjects by key from [startKey] until [limit]
 * It will only fetch the changes [fromVersion] (Inclusive) until [maxVersions] (Default=1000) is reached.
 * Can also contain a [filter], [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order]
 */
fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> DM.scanChanges(
    startKey: Key<DM>? = null,
    filter: IsFilter? = null,
    order: IsOrder? = null,
    limit: UInt = 100u,
    fromVersion: ULong = 0uL,
    toVersion: ULong? = null,
    maxVersions: UInt = 1u,
    select: RootPropRefGraph<P>? = null,
    filterSoftDeleted: Boolean = true
) =
    ScanChangesRequest(
        this,
        startKey,
        filter,
        order,
        limit,
        fromVersion,
        toVersion,
        maxVersions,
        select,
        filterSoftDeleted
    )

/**
 * A Request to scan DataObjects by key from [startKey] until [limit] for specific [dataModel]
 * It will only fetch the changes [fromVersion] (Inclusive) until [maxVersions] (Default=1000) is reached.
 * Can also contain a [filter], [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order] and only selected properties can be returned with a [select] graph
 */
@Suppress("EXPERIMENTAL_OVERRIDE")
data class ScanChangesRequest<DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> internal constructor(
    override val dataModel: DM,
    override val startKey: Key<DM>? = null,
    override val filter: IsFilter? = null,
    override val order: IsOrder? = null,
    override val limit: UInt = 100u,
    override val fromVersion: ULong = 0uL,
    override val toVersion: ULong? = null,
    override val maxVersions: UInt = 1u,
    override val select: RootPropRefGraph<P>? = null,
    override val filterSoftDeleted: Boolean = true
) : IsScanRequest<DM, P, ChangesResponse<DM>>, IsChangesRequest<DM, P, ChangesResponse<DM>> {
    override val requestType = RequestType.ScanChanges
    @Suppress("UNCHECKED_CAST")
    override val responseModel = ChangesResponse as IsObjectDataModel<ChangesResponse<DM>, *>

    @Suppress("unused")
    object Properties : ObjectPropertyDefinitions<ScanChangesRequest<*, *>>() {
        val dataModel = IsObjectRequest.addDataModel("from", this, ScanChangesRequest<*, *>::dataModel)
        val startKey = IsScanRequest.addStartKey(this, ScanChangesRequest<*, *>::startKey)
        val select = IsFetchRequest.addSelect(this, ScanChangesRequest<*, *>::select)
        val filter = IsFetchRequest.addFilter(this, ScanChangesRequest<*, *>::filter)
        val toVersion = IsFetchRequest.addToVersion(this, ScanChangesRequest<*, *>::toVersion)
        val filterSoftDeleted = IsFetchRequest.addFilterSoftDeleted(this, ScanChangesRequest<*, *>::filterSoftDeleted)
        val order = IsScanRequest.addOrder(this, ScanChangesRequest<*, *>::order)
        val limit = IsScanRequest.addLimit(this, ScanChangesRequest<*, *>::limit)
        val fromVersion = IsChangesRequest.addFromVersion(9, this, ScanChangesRequest<*, *>::fromVersion)
        val maxVersions = IsChangesRequest.addMaxVersions(10, this, ScanChangesRequest<*, *>::maxVersions)
    }

    companion object : QueryDataModel<ScanChangesRequest<*, *>, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<ScanChangesRequest<*, *>, Properties>) =
            ScanChangesRequest<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>(
                dataModel = values(1),
                startKey = values(2),
                select = values(3),
                filter = values(4),
                toVersion = values(5),
                filterSoftDeleted = values(6),
                order = values(7),
                limit = values(8),
                fromVersion = values(9),
                maxVersions = values(10)
            )
    }
}
