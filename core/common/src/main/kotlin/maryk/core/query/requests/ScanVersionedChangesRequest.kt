package maryk.core.query.requests

import maryk.core.models.RootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.definitions.PropertyDefinitions
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
 * Creates a request to scan DataObjects by key from [startKey] until [limit]
 * It will only fetch the changes [fromVersion] (Inclusive) until [maxVersions] (Default=1000) is reached.
 * Can also contain a [filter], [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order]
 */
fun <DO: Any, P: PropertyDefinitions<DO>> RootDataModel<DO, P>.scanVersionedChanges(
    startKey: Key<DO>,
    filter: IsFilter? = null,
    order: Order? = null,
    limit: UInt32 = 100.toUInt32(),
    fromVersion: UInt64,
    toVersion: UInt64? = null,
    maxVersions: UInt32 = 1000.toUInt32(),
    select: RootPropRefGraph<DO>? = null,
    filterSoftDeleted: Boolean = true
) =
    ScanVersionedChangesRequest(this, startKey, filter, order, limit, fromVersion, toVersion, maxVersions, select, filterSoftDeleted)

/**
 * A Request to scan DataObjects by key from [startKey] until [limit] for specific [dataModel]
 * It will only fetch the changes [fromVersion] (Inclusive) until [maxVersions] (Default=1000) is reached.
 * Can also contain a [filter], [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order] and only selected properties can be returned with a [select] graph
 */
data class ScanVersionedChangesRequest<DO: Any, out DM: RootDataModel<DO, *>> internal constructor(
    override val dataModel: DM,
    override val startKey: Key<DO>,
    override val filter: IsFilter? = null,
    override val order: Order? = null,
    override val limit: UInt32 = 100.toUInt32(),
    override val fromVersion: UInt64,
    override val toVersion: UInt64? = null,
    override val maxVersions: UInt32 = 1000.toUInt32(),
    override val select: RootPropRefGraph<DO>? = null,
    override val filterSoftDeleted: Boolean = true
) : IsScanRequest<DO, DM>, IsVersionedChangesRequest<DO, DM> {
    override val requestType = RequestType.ScanVersionedChanges

    internal companion object: SimpleQueryDataModel<ScanVersionedChangesRequest<*, *>>(
        properties = object : PropertyDefinitions<ScanVersionedChangesRequest<*, *>>() {
            init {
                IsObjectRequest.addDataModel(this, ScanVersionedChangesRequest<*, *>::dataModel)
                IsScanRequest.addStartKey(this, ScanVersionedChangesRequest<*, *>::startKey)
                IsFetchRequest.addFilter(this) {
                    it.filter?.let { TypedValue(it.filterType, it) }
                }
                IsFetchRequest.addOrder(this, ScanVersionedChangesRequest<*, *>::order)
                IsFetchRequest.addToVersion(this, ScanVersionedChangesRequest<*, *>::toVersion)
                IsFetchRequest.addFilterSoftDeleted(this, ScanVersionedChangesRequest<*, *>::filterSoftDeleted)
                IsScanRequest.addLimit(this, ScanVersionedChangesRequest<*, *>::limit)
                IsChangesRequest.addFromVersion(7, this, ScanVersionedChangesRequest<*, *>::fromVersion)
                IsVersionedChangesRequest.addMaxVersions(8, this, ScanVersionedChangesRequest<*, *>::maxVersions)
                IsSelectRequest.addSelect(9, this, ScanVersionedChangesRequest<*, *>::select)
            }
        }
    ) {
        override fun invoke(map: Map<Int, *>) = ScanVersionedChangesRequest(
            dataModel = map<RootDataModel<Any, *>>(0),
            startKey = map(1),
            filter = map<TypedValue<FilterType, IsFilter>?>(2)?.value,
            order = map(3),
            toVersion = map(4),
            filterSoftDeleted = map(5),
            limit = map(6),
            fromVersion = map(7),
            maxVersions = map(8),
            select = map(9)
        )
    }
}
