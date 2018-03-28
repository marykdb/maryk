package maryk.core.query.requests

import maryk.core.objects.QueryDataModel
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.Order
import maryk.core.query.filters.FilterType
import maryk.core.query.filters.IsFilter

/**
 * A Request to get changes on [dataModel] by [keys]
 * It will only fetch the changes [fromVersion] (Inclusive) until [maxVersions] (Default=1000) is reached.
 * Can also contain a [filter], [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order]
 */
data class GetChangesRequest<DO: Any, out DM: RootDataModel<DO, *>>(
    override val dataModel: DM,
    override val keys: List<Key<DO>>,
    override val filter: IsFilter? = null,
    override val order: Order? = null,
    override val fromVersion: UInt64,
    override val toVersion: UInt64? = null,
    override val filterSoftDeleted: Boolean = true
) : IsGetRequest<DO, DM>, IsChangesRequest<DO, DM> {
    constructor(
        dataModel: DM,
        vararg key: Key<DO>,
        filter: IsFilter? = null,
        order: Order? = null,
        fromVersion: UInt64,
        toVersion: UInt64? = null,
        filterSoftDeleted: Boolean = true
    ) : this(dataModel, key.toList(), filter, order, fromVersion, toVersion, filterSoftDeleted)

    internal companion object: QueryDataModel<GetChangesRequest<*, *>>(
        properties = object : PropertyDefinitions<GetChangesRequest<*, *>>() {
            init {
                IsObjectRequest.addDataModel(this, GetChangesRequest<*, *>::dataModel)
                IsGetRequest.addKeys(this, GetChangesRequest<*, *>::keys)
                IsFetchRequest.addFilter(this) {
                    it.filter?.let { TypedValue(it.filterType, it) }
                }
                IsFetchRequest.addOrder(this, GetChangesRequest<*, *>::order)
                IsFetchRequest.addToVersion(this, GetChangesRequest<*, *>::toVersion)
                IsFetchRequest.addFilterSoftDeleted(this, GetChangesRequest<*, *>::filterSoftDeleted)
                IsChangesRequest.addFromVersion(6, this, GetChangesRequest<*, *>::fromVersion)
            }
        }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = GetChangesRequest(
            dataModel = map[0] as RootDataModel<Any, *>,
            keys = map[1] as List<Key<Any>>,
            filter = (map[2] as TypedValue<FilterType, IsFilter>?)?.value,
            order = map[3] as Order?,
            toVersion = map[4] as UInt64?,
            filterSoftDeleted = map[5] as Boolean,
            fromVersion = map[6] as UInt64
        )
    }
}
