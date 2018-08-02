package maryk.core.query.requests

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.SimpleObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.Key
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.UInt64
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.query.Order
import maryk.core.query.filters.IsFilter

/**
 * Creates a Request to scan DataObjects by key from [startKey] until [limit] and only return [select]
 * values of properties.
 * Can also contain a [filter], [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order]
 */
fun <DM: IsRootDataModel<*>> DM.scan(
    startKey: Key<DM>,
    select: RootPropRefGraph<DM>? = null,
    filter: IsFilter? = null,
    order: Order? = null,
    limit: UInt32 = 100.toUInt32(),
    toVersion: UInt64? = null,
    filterSoftDeleted: Boolean = true
) =
    ScanRequest(this, startKey, select, filter, order, limit, toVersion, filterSoftDeleted)

/**
 * A Request to scan DataObjects by key from [startKey] until [limit]
 * for specific [dataModel] and only return [select]
 * values of properties.
 * Can also contain a [filter], [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order]
 */
data class ScanRequest<DM: IsRootDataModel<*>> internal constructor(
    override val dataModel: DM,
    override val startKey: Key<DM>,
    override val select: RootPropRefGraph<DM>? = null,
    override val filter: IsFilter? = null,
    override val order: Order? = null,
    override val limit: UInt32 = 100.toUInt32(),
    override val toVersion: UInt64? = null,
    override val filterSoftDeleted: Boolean = true
) : IsScanRequest<DM> {
    override val requestType = RequestType.Scan

    internal companion object: SimpleQueryDataModel<ScanRequest<*>>(
        properties = object : ObjectPropertyDefinitions<ScanRequest<*>>() {
            init {
                IsObjectRequest.addDataModel(this, ScanRequest<*>::dataModel)
                IsScanRequest.addStartKey(this, ScanRequest<*>::startKey)
                IsFetchRequest.addSelect(this, ScanRequest<*>::select)
                IsFetchRequest.addFilter(this, ScanRequest<*>::filter)
                IsFetchRequest.addOrder(this, ScanRequest<*>::order)
                IsFetchRequest.addToVersion(this, ScanRequest<*>::toVersion)
                IsFetchRequest.addFilterSoftDeleted(this, ScanRequest<*>::filterSoftDeleted)
                IsScanRequest.addLimit(this, ScanRequest<*>::limit)
            }
        }
    ) {
        override fun invoke(map: SimpleObjectValues<ScanRequest<*>>) = ScanRequest(
            dataModel = map(1),
            startKey = map(2),
            select = map(3),
            filter = map(4),
            order = map(5),
            toVersion = map(6),
            filterSoftDeleted = map(7),
            limit = map(8)
        )
    }
}
