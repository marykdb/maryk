package maryk.core.query.requests

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.SimpleObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.UInt64
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.query.Order
import maryk.core.query.filters.FilterType
import maryk.core.query.filters.IsFilter

/**
 * Creates a request to scan DataObjects by key from [startKey] [fromVersion] until [limit]
 * It will only fetch the changes [fromVersion] (Inclusive).
 * Can also contain a [filter], [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order]
 */
fun <DM: IsRootDataModel<*>> DM.scanChanges(
    startKey: Key<DM>,
    filter: IsFilter? = null,
    order: Order? = null,
    limit: UInt32 = 100.toUInt32(),
    fromVersion: UInt64,
    toVersion: UInt64? = null,
    select: RootPropRefGraph<DM>? = null,
    filterSoftDeleted: Boolean = true
) =
    ScanChangesRequest(this, startKey, filter, order, limit, fromVersion, toVersion, select, filterSoftDeleted)

/**
 * A Request to scan DataObjects by key from [startKey] [fromVersion] until [limit]
 * for specific [dataModel]
 * It will only fetch the changes [fromVersion] (Inclusive).
 * Can also contain a [filter], [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order] and only selected properties can be returned with a [select] graph
 */
data class ScanChangesRequest<DM: IsRootDataModel<*>> internal constructor(
    override val dataModel: DM,
    override val startKey: Key<DM>,
    override val filter: IsFilter? = null,
    override val order: Order? = null,
    override val limit: UInt32 = 100.toUInt32(),
    override val fromVersion: UInt64,
    override val toVersion: UInt64? = null,
    override val select: RootPropRefGraph<DM>? = null,
    override val filterSoftDeleted: Boolean = true
) : IsScanRequest<DM>, IsChangesRequest<DM> {
    override val requestType = RequestType.ScanChanges

    internal companion object: SimpleQueryDataModel<ScanChangesRequest<*>>(
        properties = object : ObjectPropertyDefinitions<ScanChangesRequest<*>>() {
            init {
                IsObjectRequest.addDataModel(this, ScanChangesRequest<*>::dataModel)
                IsScanRequest.addStartKey(this, ScanChangesRequest<*>::startKey)
                IsFetchRequest.addFilter(this) { request ->
                    request.filter?.let { TypedValue(it.filterType, it) }
                }
                IsFetchRequest.addOrder(this, ScanChangesRequest<*>::order)
                IsFetchRequest.addToVersion(this, ScanChangesRequest<*>::toVersion)
                IsFetchRequest.addFilterSoftDeleted(this, ScanChangesRequest<*>::filterSoftDeleted)
                IsScanRequest.addLimit(this, ScanChangesRequest<*>::limit)
                IsChangesRequest.addFromVersion(7, this, ScanChangesRequest<*>::fromVersion)
                IsFetchRequest.addSelect(8, this, ScanChangesRequest<*>::select)
            }
        }
    ) {
        override fun invoke(map: SimpleObjectValues<ScanChangesRequest<*>>) = ScanChangesRequest(
            dataModel = map(0),
            startKey = map(1),
            filter = map<TypedValue<FilterType, IsFilter>?>(2)?.value,
            order = map(3),
            toVersion = map(4),
            filterSoftDeleted = map(5),
            limit = map(6),
            fromVersion = map(7),
            select = map(8)
        )
    }
}
