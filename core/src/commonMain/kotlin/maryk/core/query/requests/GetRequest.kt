package maryk.core.query.requests

import maryk.core.aggregations.Aggregations
import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.IsRootDataModel
import maryk.core.models.QueryModel
import maryk.core.properties.definitions.boolean
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.definitions.embedObject
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.number
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.Key
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.RequestContext
import maryk.core.query.filters.IsFilter
import maryk.core.query.requests.RequestType.Get
import maryk.core.query.responses.ValuesResponse
import maryk.core.values.ObjectValues

/**
 * Creates a Request to get [select] values of DataObjects by [keys] and [where] filter for the DataModel of type [DM].
 * Optional: the data can be requested as it was at [toVersion]
 * If [filterSoftDeleted] (default true) is set to false it will not where away all soft deleted results.
 */
fun <DM : IsRootDataModel> DM.get(
    vararg keys: Key<DM>,
    select: RootPropRefGraph<DM>? = null,
    where: IsFilter? = null,
    toVersion: ULong? = null,
    filterSoftDeleted: Boolean = true,
    aggregations: Aggregations? = null
) =
    GetRequest(this, keys.toList(), select, where, toVersion, filterSoftDeleted, aggregations)

/**
 * A Request to get [select] values of DataObjects by [keys] and [where] filter for specific DataModel of type [DM].
 * Optional: the data can be requested as it was at [toVersion]
 * If [filterSoftDeleted] (default true) is set to false it will not filter away all soft deleted results.
 */
data class GetRequest<DM : IsRootDataModel> internal constructor(
    override val dataModel: DM,
    override val keys: List<Key<DM>>,
    override val select: RootPropRefGraph<DM>? = null,
    override val where: IsFilter?,
    override val toVersion: ULong?,
    override val filterSoftDeleted: Boolean,
    override val aggregations: Aggregations? = null
) : IsGetRequest<DM, ValuesResponse<DM>>, IsTransportableRequest<ValuesResponse<DM>> {
    override val requestType = Get
    override val responseModel = ValuesResponse

    companion object : QueryModel<GetRequest<*>, Companion>() {
        val from by addDataModel { it.dataModel }
        val keys by list(
            index = 2u,
            getter = GetRequest<*>::keys,
            valueDefinition = ContextualReferenceDefinition<RequestContext>(
                contextualResolver = {
                    it?.dataModel as? IsRootDataModel ?: throw ContextNotFoundException()
                }
            )
        )
        val select by embedObject(3u, GetRequest<*>::select, dataModel = { RootPropRefGraph })
        val where by addFilter(GetRequest<*>::where)
        val toVersion by number(5u, GetRequest<*>::toVersion, UInt64, required = false)
        val filterSoftDeleted  by boolean(6u, GetRequest<*>::filterSoftDeleted, default = true)
        val aggregations by embedObject(7u, GetRequest<*>::aggregations, dataModel = { Aggregations }, alternativeNames = setOf("aggs"))

        override fun invoke(values: ObjectValues<GetRequest<*>, Companion>) = GetRequest(
            dataModel = values(1u),
            keys = values(2u),
            select = values(3u),
            where = values(4u),
            toVersion = values(5u),
            filterSoftDeleted = values(6u),
            aggregations = values(7u)
        )
    }
}
