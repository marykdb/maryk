package maryk.core.query.requests

import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.QueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.Key
import maryk.core.query.filters.IsFilter
import maryk.core.query.responses.ValuesResponse
import maryk.core.values.ObjectValues

/**
 * Creates a Request to get [select] values of DataObjects by [keys] and [where] filter for the DataModel of type [DM].
 * Optional: the data can be requested as it was at [toVersion]
 * If [filterSoftDeleted] (default true) is set to false it will not where away all soft deleted results.
 */
fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> DM.get(
    vararg keys: Key<DM>,
    select: RootPropRefGraph<P>? = null,
    where: IsFilter? = null,
    toVersion: ULong? = null,
    filterSoftDeleted: Boolean = true
) =
    GetRequest(this, keys.toList(), select, where, toVersion, filterSoftDeleted)

/**
 * A Request to get [select] values of DataObjects by [keys] and [where] filter for specific DataModel of type [DM].
 * Optional: the data can be requested as it was at [toVersion]
 * If [filterSoftDeleted] (default true) is set to false it will not filter away all soft deleted results.
 */
data class GetRequest<DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> internal constructor(
    override val dataModel: DM,
    override val keys: List<Key<DM>>,
    override val select: RootPropRefGraph<P>? = null,
    override val where: IsFilter?,
    override val toVersion: ULong?,
    override val filterSoftDeleted: Boolean
) : IsGetRequest<DM, P, ValuesResponse<DM, P>> {
    override val requestType = RequestType.Get
    @Suppress("UNCHECKED_CAST")
    override val responseModel = ValuesResponse as IsObjectDataModel<ValuesResponse<DM, P>, *>

    object Properties : ObjectPropertyDefinitions<GetRequest<*, *>>() {
        val dataModel = IsObjectRequest.addDataModel("from", this, GetRequest<*, *>::dataModel)
        val keys = IsGetRequest.addKeys(this, GetRequest<*, *>::keys)
        val select = IsFetchRequest.addSelect(this, GetRequest<*, *>::select)
        val where = IsFetchRequest.addFilter(this, GetRequest<*, *>::where)
        val toVersion = IsFetchRequest.addToVersion(this, GetRequest<*, *>::toVersion)
        val filterSoftDeleted = IsFetchRequest.addFilterSoftDeleted(this, GetRequest<*, *>::filterSoftDeleted)
    }

    companion object : QueryDataModel<GetRequest<*, *>, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<GetRequest<*, *>, Properties>) = GetRequest(
            dataModel = values<IsRootValuesDataModel<PropertyDefinitions>>(1),
            keys = values(2),
            select = values(3),
            where = values(4),
            toVersion = values(5),
            filterSoftDeleted = values(6)
        )
    }
}
