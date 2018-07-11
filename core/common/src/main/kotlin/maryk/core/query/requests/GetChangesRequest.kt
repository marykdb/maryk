package maryk.core.query.requests

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.SimpleObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.Order
import maryk.core.query.filters.FilterType
import maryk.core.query.filters.IsFilter

/**
 * Creates a Request to get changes by [keys] from a store
 * It will only fetch the changes [fromVersion] (Inclusive).
 * Can also contain a [filter], [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order]
 */
fun <DM: IsRootDataModel<P>, P: ObjectPropertyDefinitions<*>> DM.getChanges(
    vararg keys: Key<DM>,
    filter: IsFilter? = null,
    order: Order? = null,
    fromVersion: UInt64,
    toVersion: UInt64? = null,
    select: RootPropRefGraph<DM>? = null,
    filterSoftDeleted: Boolean = true
) =
    GetChangesRequest(this, keys.toList(), filter, order, fromVersion, toVersion, select, filterSoftDeleted)

/**
 * A Request to get changes on [dataModel] by [keys]
 * It will only fetch the changes [fromVersion] (Inclusive).
 * Can also contain a [filter], [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order] and only selected properties can be returned with a [select] graph
 */
data class GetChangesRequest<DM: IsRootDataModel<*>> internal constructor(
    override val dataModel: DM,
    override val keys: List<Key<DM>>,
    override val filter: IsFilter? = null,
    override val order: Order? = null,
    override val fromVersion: UInt64,
    override val toVersion: UInt64? = null,
    override val select: RootPropRefGraph<DM>? = null,
    override val filterSoftDeleted: Boolean = true
) : IsGetRequest<DM>, IsChangesRequest<DM> {
    override val requestType = RequestType.GetChanges

    internal companion object: SimpleQueryDataModel<GetChangesRequest<*>>(
        properties = object : ObjectPropertyDefinitions<GetChangesRequest<*>>() {
            init {
                IsObjectRequest.addDataModel(this, GetChangesRequest<*>::dataModel)
                IsGetRequest.addKeys(this, GetChangesRequest<*>::keys)
                IsFetchRequest.addFilter(this) { request ->
                    request.filter?.let { TypedValue(it.filterType, it) }
                }
                IsFetchRequest.addOrder(this, GetChangesRequest<*>::order)
                IsFetchRequest.addToVersion(this, GetChangesRequest<*>::toVersion)
                IsFetchRequest.addFilterSoftDeleted(this, GetChangesRequest<*>::filterSoftDeleted)
                IsChangesRequest.addFromVersion(6, this, GetChangesRequest<*>::fromVersion)
                IsSelectRequest.addSelect(7, this, GetChangesRequest<*>::select)
            }
        }
    ) {
        override fun invoke(map: SimpleObjectValues<GetChangesRequest<*>>) = GetChangesRequest(
            dataModel = map(0),
            keys = map(1),
            filter = map<TypedValue<FilterType, IsFilter>?>(2)?.value,
            order = map(3),
            toVersion = map(4),
            filterSoftDeleted = map(5),
            fromVersion = map(6),
            select = map(7)
        )
    }
}
