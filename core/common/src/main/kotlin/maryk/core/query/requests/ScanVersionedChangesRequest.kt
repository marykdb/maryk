package maryk.core.query.requests

import maryk.core.objects.QueryDataModel
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions
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
    filterSoftDeleted: Boolean = true
) =
    ScanVersionedChangesRequest(this, startKey, filter, order, limit, fromVersion, toVersion, maxVersions, filterSoftDeleted)

/**
 * A Request to scan DataObjects by key from [startKey] until [limit] for specific [dataModel]
 * It will only fetch the changes [fromVersion] (Inclusive) until [maxVersions] (Default=1000) is reached.
 * Can also contain a [filter], [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order]
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
    override val filterSoftDeleted: Boolean = true
) : IsScanRequest<DO, DM>, IsVersionedChangesRequest<DO, DM> {
    internal companion object: QueryDataModel<ScanVersionedChangesRequest<*, *>>(
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
            }
        }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = ScanVersionedChangesRequest(
            dataModel = map[0] as RootDataModel<Any, *>,
            startKey = map[1] as Key<Any>,
            filter = (map[2] as TypedValue<FilterType, IsFilter>?)?.value,
            order = map[3] as Order?,
            toVersion = map[4] as UInt64?,
            filterSoftDeleted = map[5] as Boolean? ?: true,
            limit = map[6] as UInt32? ?: 100.toUInt32(),
            fromVersion = map[7] as UInt64,
            maxVersions = map[8] as UInt32? ?: 1000.toUInt32()
        )
    }
}
