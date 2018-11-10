@file:Suppress("EXPERIMENTAL_OVERRIDE", "EXPERIMENTAL_API_USAGE", "EXPERIMENTAL_UNSIGNED_LITERALS", "unused")

package maryk.core.query.requests

import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.QueryDataModel
import maryk.core.objects.ObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.Key
import maryk.core.query.Order
import maryk.core.query.filters.IsFilter
import maryk.core.query.responses.ChangesResponse

/**
 * Creates a request to scan DataObjects by key from [startKey] [fromVersion] until [limit]
 * It will only fetch the changes [fromVersion] (Inclusive).
 * Can also contain a [filter], [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order]
 */
fun <DM: IsRootValuesDataModel<*>> DM.scanChanges(
    startKey: Key<DM>,
    filter: IsFilter? = null,
    order: Order? = null,
    limit: UInt = 100u,
    fromVersion: ULong,
    toVersion: ULong? = null,
    select: RootPropRefGraph<DM>? = null,
    filterSoftDeleted: Boolean = true
) =
    ScanChangesRequest(this, startKey, select, filter, order, limit, fromVersion, toVersion, filterSoftDeleted)

/**
 * A Request to scan DataObjects by key from [startKey] [fromVersion] until [limit]
 * for specific [dataModel]
 * It will only fetch the changes [fromVersion] (Inclusive).
 * Can also contain a [filter], [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order] and only selected properties can be returned with a [select] graph
 */
data class ScanChangesRequest<DM: IsRootValuesDataModel<*>> internal constructor(
    override val dataModel: DM,
    override val startKey: Key<DM>,
    override val select: RootPropRefGraph<DM>? = null,
    override val filter: IsFilter? = null,
    override val order: Order? = null,
    override val limit: UInt = 100u,
    override val fromVersion: ULong,
    override val toVersion: ULong? = null,
    override val filterSoftDeleted: Boolean = true
) : IsScanRequest<DM, ChangesResponse<DM>>, IsChangesRequest<DM, ChangesResponse<DM>> {
    override val requestType = RequestType.ScanChanges
    @Suppress("UNCHECKED_CAST")
    override val responseModel = ChangesResponse as IsObjectDataModel<ChangesResponse<DM>, *>

    @Suppress("unused")
    object Properties : ObjectPropertyDefinitions<ScanChangesRequest<*>>() {
        val dataModel = IsObjectRequest.addDataModel(this, ScanChangesRequest<*>::dataModel)
        val startKey = IsScanRequest.addStartKey(this, ScanChangesRequest<*>::startKey)
        val select = IsFetchRequest.addSelect(this, ScanChangesRequest<*>::select)
        val filter = IsFetchRequest.addFilter(this, ScanChangesRequest<*>::filter)
        val order = IsFetchRequest.addOrder(this, ScanChangesRequest<*>::order)
        val addToVersion = IsFetchRequest.addToVersion(this, ScanChangesRequest<*>::toVersion)
        val filterSoftDeleted = IsFetchRequest.addFilterSoftDeleted(this, ScanChangesRequest<*>::filterSoftDeleted)
        val limit = IsScanRequest.addLimit(this, ScanChangesRequest<*>::limit)
        val fromVersion = IsChangesRequest.addFromVersion(9, this, ScanChangesRequest<*>::fromVersion)
    }

    companion object: QueryDataModel<ScanChangesRequest<*>, Properties>(
        properties = Properties
    ) {
        override fun invoke(map: ObjectValues<ScanChangesRequest<*>, Properties>) = ScanChangesRequest(
            dataModel = map(1),
            startKey = map(2),
            select = map(3),
            filter = map(4),
            order = map(5),
            toVersion = map(6),
            filterSoftDeleted = map(7),
            limit = map(8),
            fromVersion = map(9)
        )
    }
}
