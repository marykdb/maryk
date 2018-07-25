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
 * Creates a request to scan DataObjects by key from [startKey] until [limit]
 * It will only fetch the changes [fromVersion] (Inclusive) until [maxVersions] (Default=1000) is reached.
 * Can also contain a [filter], [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order]
 */
fun <DM: IsRootDataModel<*>> DM.scanVersionedChanges(
    startKey: Key<DM>,
    filter: IsFilter? = null,
    order: Order? = null,
    limit: UInt32 = 100.toUInt32(),
    fromVersion: UInt64,
    toVersion: UInt64? = null,
    maxVersions: UInt32 = 1000.toUInt32(),
    select: RootPropRefGraph<DM>? = null,
    filterSoftDeleted: Boolean = true
) =
    ScanVersionedChangesRequest(this, startKey, filter, order, limit, fromVersion, toVersion, maxVersions, select, filterSoftDeleted)

/**
 * A Request to scan DataObjects by key from [startKey] until [limit] for specific [dataModel]
 * It will only fetch the changes [fromVersion] (Inclusive) until [maxVersions] (Default=1000) is reached.
 * Can also contain a [filter], [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order] and only selected properties can be returned with a [select] graph
 */
data class ScanVersionedChangesRequest<DM: IsRootDataModel<*>> internal constructor(
    override val dataModel: DM,
    override val startKey: Key<DM>,
    override val filter: IsFilter? = null,
    override val order: Order? = null,
    override val limit: UInt32 = 100.toUInt32(),
    override val fromVersion: UInt64,
    override val toVersion: UInt64? = null,
    override val maxVersions: UInt32 = 1000.toUInt32(),
    override val select: RootPropRefGraph<DM>? = null,
    override val filterSoftDeleted: Boolean = true
) : IsScanRequest<DM>, IsVersionedChangesRequest<DM> {
    override val requestType = RequestType.ScanVersionedChanges

    internal companion object: SimpleQueryDataModel<ScanVersionedChangesRequest<*>>(
        properties = object : ObjectPropertyDefinitions<ScanVersionedChangesRequest<*>>() {
            init {
                IsObjectRequest.addDataModel(this, ScanVersionedChangesRequest<*>::dataModel)
                IsScanRequest.addStartKey(this, ScanVersionedChangesRequest<*>::startKey)
                IsFetchRequest.addSelect(this, ScanVersionedChangesRequest<*>::select)
                IsFetchRequest.addFilter(this) { request ->
                    request.filter?.let { TypedValue(it.filterType, it) }
                }
                IsFetchRequest.addOrder(this, ScanVersionedChangesRequest<*>::order)
                IsFetchRequest.addToVersion(this, ScanVersionedChangesRequest<*>::toVersion)
                IsFetchRequest.addFilterSoftDeleted(this, ScanVersionedChangesRequest<*>::filterSoftDeleted)
                IsScanRequest.addLimit(this, ScanVersionedChangesRequest<*>::limit)
                IsChangesRequest.addFromVersion(9, this, ScanVersionedChangesRequest<*>::fromVersion)
                IsVersionedChangesRequest.addMaxVersions(10, this, ScanVersionedChangesRequest<*>::maxVersions)
            }
        }
    ) {
        override fun invoke(map: SimpleObjectValues<ScanVersionedChangesRequest<*>>) = ScanVersionedChangesRequest(
            dataModel = map(1),
            startKey = map(2),
            select = map(3),
            filter = map<TypedValue<FilterType, IsFilter>?>(4)?.value,
            order = map(5),
            toVersion = map(6),
            filterSoftDeleted = map(7),
            limit = map(8),
            fromVersion = map(9),
            maxVersions = map(10)
        )
    }
}
