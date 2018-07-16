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
 * Creates a Request to get [select] values of DataObjects of type [DO] by [keys] and [filter] for the DataModel.
 * Optional: [order] can be applied to the results and the data can be shown as it was at [toVersion]
 * If [filterSoftDeleted] (default true) is set to false it will not filter away all soft deleted results.
 */
fun <DM: IsRootDataModel<*>> DM.get(
    vararg keys: Key<DM>,
    select: RootPropRefGraph<DM>? = null,
    filter: IsFilter? = null,
    order: Order? = null,
    toVersion: UInt64? = null,
    filterSoftDeleted: Boolean = true
) =
    GetRequest(this, keys.toList(), select, filter, order, toVersion, filterSoftDeleted)

/**
 * A Request to get [select] values of DataObjects of type [DO] by [keys] and [filter] for specific DataModel of type [DM].
 * Optional: [order] can be applied to the results and the data can be shown as it was at [toVersion]
 * If [filterSoftDeleted] (default true) is set to false it will not filter away all soft deleted results.
 */
data class GetRequest<DM: IsRootDataModel<*>> internal constructor(
    override val dataModel: DM,
    override val keys: List<Key<DM>>,
    override val select: RootPropRefGraph<DM>? = null,
    override val filter: IsFilter?,
    override val order: Order?,
    override val toVersion: UInt64?,
    override val filterSoftDeleted: Boolean
) : IsGetRequest<DM> {
    override val requestType = RequestType.Get

    internal companion object: SimpleQueryDataModel<GetRequest<*>>(
        properties = object : ObjectPropertyDefinitions<GetRequest<*>>() {
            init {
                IsObjectRequest.addDataModel(this, GetRequest<*>::dataModel)
                IsGetRequest.addKeys(this, GetRequest<*>::keys)
                IsFetchRequest.addSelect(this, GetRequest<*>::select)
                IsFetchRequest.addFilter(this) { request ->
                    request.filter?.let { TypedValue(it.filterType, it) }
                }
                IsFetchRequest.addOrder(this, GetRequest<*>::order)
                IsFetchRequest.addToVersion(this, GetRequest<*>::toVersion)
                IsFetchRequest.addFilterSoftDeleted(this, GetRequest<*>::filterSoftDeleted)
            }
        }
    ) {
        override fun invoke(map: SimpleObjectValues<GetRequest<*>>) = GetRequest(
            dataModel = map(0),
            keys = map(1),
            select = map(2),
            filter = map<TypedValue<FilterType, IsFilter>?>(3)?.value,
            order = map(4),
            toVersion = map(5),
            filterSoftDeleted = map(6)
        )
    }
}
