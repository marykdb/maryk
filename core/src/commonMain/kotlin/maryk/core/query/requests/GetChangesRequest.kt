@file:Suppress("EXPERIMENTAL_API_USAGE", "unused")

package maryk.core.query.requests

import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.QueryDataModel
import maryk.core.values.ObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.Key
import maryk.core.query.Order
import maryk.core.query.filters.IsFilter
import maryk.core.query.responses.ChangesResponse

/**
 * Creates a Request to get changes by [keys] from a store
 * It will only fetch the changes [fromVersion] (Inclusive).
 * Can also contain a [filter], [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order]
 */
fun <DM: IsRootValuesDataModel<*>> DM.getChanges(
    vararg keys: Key<DM>,
    filter: IsFilter? = null,
    order: Order? = null,
    fromVersion: ULong,
    toVersion: ULong? = null,
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
data class GetChangesRequest<DM: IsRootValuesDataModel<*>> internal constructor(
    override val dataModel: DM,
    override val keys: List<Key<DM>>,
    override val filter: IsFilter? = null,
    override val order: Order? = null,
    override val fromVersion: ULong,
    override val toVersion: ULong? = null,
    override val select: RootPropRefGraph<DM>? = null,
    override val filterSoftDeleted: Boolean = true
) : IsGetRequest<DM, ChangesResponse<DM>>, IsChangesRequest<DM, ChangesResponse<DM>> {
    override val requestType = RequestType.GetChanges
    @Suppress("UNCHECKED_CAST")
    override val responseModel = ChangesResponse as IsObjectDataModel<ChangesResponse<DM>, *>

    @Suppress("unused")
    object Properties : ObjectPropertyDefinitions<GetChangesRequest<*>>() {
        val dataModel = IsObjectRequest.addDataModel(this, GetChangesRequest<*>::dataModel)
        val keys = IsGetRequest.addKeys(this, GetChangesRequest<*>::keys)
        val select = IsFetchRequest.addSelect(this, GetChangesRequest<*>::select)
        val filter = IsFetchRequest.addFilter(this, GetChangesRequest<*>::filter)
        val order = IsFetchRequest.addOrder(this, GetChangesRequest<*>::order)
        val addToVersion = IsFetchRequest.addToVersion(this, GetChangesRequest<*>::toVersion)
        val filterSoftDeleted = IsFetchRequest.addFilterSoftDeleted(this, GetChangesRequest<*>::filterSoftDeleted)
        val fromVersion = IsChangesRequest.addFromVersion(8, this, GetChangesRequest<*>::fromVersion)
    }

    companion object: QueryDataModel<GetChangesRequest<*>, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<GetChangesRequest<*>, Properties>) = GetChangesRequest(
            dataModel = values(1),
            keys = values(2),
            select = values(3),
            filter = values(4),
            order = values(5),
            toVersion = values(6),
            filterSoftDeleted = values(7),
            fromVersion = values(8)
        )
    }
}
