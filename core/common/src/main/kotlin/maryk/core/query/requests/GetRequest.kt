package maryk.core.query.requests

import maryk.core.models.IsRootDataModel
import maryk.core.models.QueryDataModel
import maryk.core.objects.ObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.Key
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.Order
import maryk.core.query.filters.IsFilter

/**
 * Creates a Request to get [select] values of DataObjects by [keys] and [filter] for the DataModel of type [DM].
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
 * A Request to get [select] values of DataObjects by [keys] and [filter] for specific DataModel of type [DM].
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

    object Properties : ObjectPropertyDefinitions<GetRequest<*>>() {
        val dataModel = IsObjectRequest.addDataModel(this, GetRequest<*>::dataModel)
        val keys = IsGetRequest.addKeys(this, GetRequest<*>::keys)
        private val select = IsFetchRequest.addSelect(this, GetRequest<*>::select)
        val filter = IsFetchRequest.addFilter(this, GetRequest<*>::filter)
        private val order = IsFetchRequest.addOrder(this, GetRequest<*>::order)
        val toVersion = IsFetchRequest.addToVersion(this, GetRequest<*>::toVersion)
        val filterSoftDeleted = IsFetchRequest.addFilterSoftDeleted(this, GetRequest<*>::filterSoftDeleted)
    }

    internal companion object: QueryDataModel<GetRequest<*>, Properties>(
        properties = Properties
    ) {
        override fun invoke(map: ObjectValues<GetRequest<*>, Properties>) = GetRequest(
            dataModel = map(1),
            keys = map(2),
            select = map(3),
            filter = map(4),
            order = map(5),
            toVersion = map(6),
            filterSoftDeleted = map(7)
        )
    }
}
