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
 * Creates a Request to scan DataObjects by key from [startKey] until [limit] and only return [select]
 * values of properties.
 * Can also contain a [filter], [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order]
 */
fun <DM: IsRootDataModel<*>> DM.scanSelect(
    startKey: Key<DM>,
    filter: IsFilter? = null,
    order: Order? = null,
    limit: UInt32 = 100.toUInt32(),
    toVersion: UInt64? = null,
    select: RootPropRefGraph<DM>,
    filterSoftDeleted: Boolean = true
) =
    ScanSelectRequest(this, startKey, filter, order, limit, toVersion, select, filterSoftDeleted)

/**
 * A Request to scan DataObjects by key from [startKey] until [limit]
 * for specific [dataModel] and only return [select]
 * values of properties.
 * Can also contain a [filter], [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order]
 */
data class ScanSelectRequest<DM: IsRootDataModel<*>> internal constructor(
    override val dataModel: DM,
    override val startKey: Key<DM>,
    override val filter: IsFilter? = null,
    override val order: Order? = null,
    override val limit: UInt32 = 100.toUInt32(),
    override val toVersion: UInt64? = null,
    override val select: RootPropRefGraph<DM>,
    override val filterSoftDeleted: Boolean = true
) : IsScanRequest<DM>, IsSelectRequest<DM> {
    override val requestType = RequestType.Scan

    internal companion object: SimpleQueryDataModel<ScanSelectRequest<*>>(
        properties = object : ObjectPropertyDefinitions<ScanSelectRequest<*>>() {
            init {
                IsObjectRequest.addDataModel(this, ScanSelectRequest<*>::dataModel)
                IsScanRequest.addStartKey(this, ScanSelectRequest<*>::startKey)
                IsFetchRequest.addFilter(this) { request ->
                    request.filter?.let { TypedValue(it.filterType, it) }
                }
                IsFetchRequest.addOrder(this, ScanSelectRequest<*>::order)
                IsFetchRequest.addToVersion(this, ScanSelectRequest<*>::toVersion)
                IsFetchRequest.addFilterSoftDeleted(this, ScanSelectRequest<*>::filterSoftDeleted)
                IsScanRequest.addLimit(this, ScanSelectRequest<*>::limit)
                IsSelectRequest.addSelect(7, this, ScanSelectRequest<*>::select)
            }
        }
    ) {
        override fun invoke(map: SimpleObjectValues<ScanSelectRequest<*>>) = ScanSelectRequest(
            dataModel = map(0),
            startKey = map(1),
            filter = map<TypedValue<FilterType, IsFilter>?>(2)?.value,
            order = map(3),
            toVersion = map(4),
            filterSoftDeleted = map(5),
            limit = map(6),
            select = map(7)
        )
    }
}
