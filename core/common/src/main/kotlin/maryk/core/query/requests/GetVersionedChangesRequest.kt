package maryk.core.query.requests

import maryk.core.models.QueryDataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.graph.RootPropRefGraph
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
 * Creates a request to get DataObject of type [DO] its versioned changes by value [keys]
 * It will only fetch the changes [fromVersion] (Inclusive) until [maxVersions] (Default=1000) is reached.
 * Can also contain a [filter], [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order]
 */
fun <DO: Any, P: PropertyDefinitions<DO>> RootDataModel<DO, P>.getVersionedChanges(
    vararg keys: Key<DO>,
    filter: IsFilter? = null,
    order: Order? = null,
    fromVersion: UInt64,
    toVersion: UInt64? = null,
    maxVersions: UInt32 = 1000.toUInt32(),
    select: RootPropRefGraph<DO>? = null,
    filterSoftDeleted: Boolean = true
) =
    GetVersionedChangesRequest(this, keys.toList(), filter, order, fromVersion, toVersion, maxVersions, select, filterSoftDeleted)

/**
 * A Request to get DataObject of type [DO] its versioned changes by value [keys] for specific [dataModel] of type [DM]
 * It will only fetch the changes [fromVersion] (Inclusive) until [maxVersions] (Default=1000) is reached.
 * Can also contain a [filter], [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order] and only selected properties can be returned with a [select] graph
 */
data class GetVersionedChangesRequest<DO: Any, out DM: RootDataModel<DO, *>> internal constructor(
    override val dataModel: DM,
    override val keys: List<Key<DO>>,
    override val filter: IsFilter? = null,
    override val order: Order? = null,
    override val fromVersion: UInt64,
    override val toVersion: UInt64? = null,
    override val maxVersions: UInt32 = 1000.toUInt32(),
    override val select: RootPropRefGraph<DO>? = null,
    override val filterSoftDeleted: Boolean = true
) : IsGetRequest<DO, DM>, IsVersionedChangesRequest<DO, DM> {
    override val requestType = RequestType.GetVersionedChanges

    internal companion object: QueryDataModel<GetVersionedChangesRequest<*, *>>(
        properties = object : PropertyDefinitions<GetVersionedChangesRequest<*, *>>() {
            init {
                IsObjectRequest.addDataModel(this, GetVersionedChangesRequest<*, *>::dataModel)
                IsGetRequest.addKeys(this, GetVersionedChangesRequest<*, *>::keys)
                IsFetchRequest.addFilter(this) {
                    it.filter?.let { TypedValue(it.filterType, it) }
                }
                IsFetchRequest.addOrder(this, GetVersionedChangesRequest<*, *>::order)
                IsFetchRequest.addToVersion(this, GetVersionedChangesRequest<*, *>::toVersion)
                IsFetchRequest.addFilterSoftDeleted(this, GetVersionedChangesRequest<*, *>::filterSoftDeleted)
                IsChangesRequest.addFromVersion(6, this, GetVersionedChangesRequest<*, *>::fromVersion)
                IsVersionedChangesRequest.addMaxVersions(7, this, GetVersionedChangesRequest<*, *>::maxVersions)
                IsSelectRequest.addSelect(8, this, GetVersionedChangesRequest<*, *>::select)
            }
        }
    ) {
        override fun invoke(map: Map<Int, *>) = GetVersionedChangesRequest(
            dataModel = map<RootDataModel<Any, *>>(0),
            keys = map(1),
            filter = map<TypedValue<FilterType, IsFilter>?>(2)?.value,
            order = map(3),
            toVersion = map(4),
            filterSoftDeleted = map(5),
            fromVersion = map(6),
            maxVersions = map(7),
            select = map(8)
        )
    }
}
