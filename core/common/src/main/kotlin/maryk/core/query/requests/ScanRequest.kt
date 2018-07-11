package maryk.core.query.requests

import maryk.core.models.RootObjectDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.SimpleObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.UInt64
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.query.Order
import maryk.core.query.filters.FilterType
import maryk.core.query.filters.IsFilter

/**
 * Creates a Request to scan DataObjects by key from [startKey] until [limit]
 * Can also contain a [filter], [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order]
 */
fun <DO: Any, P: ObjectPropertyDefinitions<DO>> RootObjectDataModel<*, DO, P>.scan(
    startKey: Key<DO>,
    filter: IsFilter? = null,
    order: Order? = null,
    limit: UInt32 = 100.toUInt32(),
    toVersion: UInt64? = null,
    filterSoftDeleted: Boolean = true
) =
    ScanRequest(this, startKey, filter, order, limit, toVersion, filterSoftDeleted)

/**
 * A Request to scan DataObjects by key from [startKey] until [limit]
 * for specific [dataModel]
 * Can also contain a [filter], [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order]
 */
data class ScanRequest<DO: Any, out DM: RootObjectDataModel<*, DO, *>> internal constructor(
    override val dataModel: DM,
    override val startKey: Key<DO>,
    override val filter: IsFilter? = null,
    override val order: Order? = null,
    override val limit: UInt32 = 100.toUInt32(),
    override val toVersion: UInt64? = null,
    override val filterSoftDeleted: Boolean = true
) : IsScanRequest<DO, DM> {
    override val requestType = RequestType.Scan

    internal companion object: SimpleQueryDataModel<ScanRequest<*, *>>(
        properties = object : ObjectPropertyDefinitions<ScanRequest<*, *>>() {
            init {
                IsObjectRequest.addDataModel(this, ScanRequest<*, *>::dataModel)
                IsScanRequest.addStartKey(this, ScanRequest<*, *>::startKey)
                IsFetchRequest.addFilter(this) { request ->
                    request.filter?.let { TypedValue(it.filterType, it) }
                }
                IsFetchRequest.addOrder(this, ScanRequest<*, *>::order)
                IsFetchRequest.addToVersion(this, ScanRequest<*, *>::toVersion)
                IsFetchRequest.addFilterSoftDeleted(this, ScanRequest<*, *>::filterSoftDeleted)
                IsScanRequest.addLimit(this, ScanRequest<*, *>::limit)
            }
        }
    ) {
        override fun invoke(map: SimpleObjectValues<ScanRequest<*, *>>) = ScanRequest(
            dataModel = map<RootObjectDataModel<*, Any, *>>(0),
            startKey = map(1),
            filter = map<TypedValue<FilterType, IsFilter>?>(2)?.value,
            order = map(3),
            toVersion = map(4),
            filterSoftDeleted = map(5),
            limit = map(6)
        )
    }
}
