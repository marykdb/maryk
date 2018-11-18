@file:Suppress("EXPERIMENTAL_API_USAGE")

package maryk.core.query.requests

import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.QueryDataModel
import maryk.core.values.ObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.Key
import maryk.core.query.Order
import maryk.core.query.filters.IsFilter
import maryk.core.query.responses.ValuesResponse

/**
 * Creates a Request to get [select] values of DataObjects by [keys] and [filter] for the DataModel of type [DM].
 * Optional: [order] can be applied to the results and the data can be shown as it was at [toVersion]
 * If [filterSoftDeleted] (default true) is set to false it will not filter away all soft deleted results.
 */
fun <DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> DM.get(
    vararg keys: Key<DM>,
    select: RootPropRefGraph<DM>? = null,
    filter: IsFilter? = null,
    order: Order? = null,
    toVersion: ULong? = null,
    filterSoftDeleted: Boolean = true
) =
    GetRequest(this, keys.toList(), select, filter, order, toVersion, filterSoftDeleted)

/**
 * A Request to get [select] values of DataObjects by [keys] and [filter] for specific DataModel of type [DM].
 * Optional: [order] can be applied to the results and the data can be shown as it was at [toVersion]
 * If [filterSoftDeleted] (default true) is set to false it will not filter away all soft deleted results.
 */
data class GetRequest<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> internal constructor(
    override val dataModel: DM,
    override val keys: List<Key<DM>>,
    override val select: RootPropRefGraph<DM>? = null,
    override val filter: IsFilter?,
    override val order: Order?,
    override val toVersion: ULong?,
    override val filterSoftDeleted: Boolean
) : IsGetRequest<DM, ValuesResponse<DM, P>> {
    override val requestType = RequestType.Get
    @Suppress("UNCHECKED_CAST")
    override val responseModel = ValuesResponse as IsObjectDataModel<ValuesResponse<DM, P>, *>

    object Properties : ObjectPropertyDefinitions<GetRequest<*, *>>() {
        val dataModel = IsObjectRequest.addDataModel(this, GetRequest<*, *>::dataModel)
        val keys = IsGetRequest.addKeys(this, GetRequest<*, *>::keys)
        val select = IsFetchRequest.addSelect(this, GetRequest<*, *>::select)
        val filter = IsFetchRequest.addFilter(this, GetRequest<*, *>::filter)
        val order = IsFetchRequest.addOrder(this, GetRequest<*, *>::order)
        val toVersion = IsFetchRequest.addToVersion(this, GetRequest<*, *>::toVersion)
        val filterSoftDeleted = IsFetchRequest.addFilterSoftDeleted(this, GetRequest<*, *>::filterSoftDeleted)
    }

    companion object: QueryDataModel<GetRequest<*, *>, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<GetRequest<*, *>, Properties>) = GetRequest(
            dataModel = values<IsRootValuesDataModel<PropertyDefinitions>>(1),
            keys = values(2),
            select = values(3),
            filter = values(4),
            order = values(5),
            toVersion = values(6),
            filterSoftDeleted = values(7)
        )
    }
}
