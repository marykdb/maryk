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
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.RequestContext
import maryk.core.query.filters.IsFilter
import maryk.core.query.requests.RequestType.GetChanges
import maryk.core.query.responses.UpdatesResponse
import maryk.core.values.ObjectValues

/**
 * Creates a request to get DataObject its versioned changes by value [keys]
 * It will only fetch the updates [fromVersion] (Inclusive) until [maxVersions] (Default=1) is reached.
 * Can also contain a [where] filter, [filterSoftDeleted], [toVersion] to further limit results.
 */
fun <DM : IsRootDataModel> DM.getUpdates(
    vararg keys: Key<DM>,
    where: IsFilter? = null,
    fromVersion: ULong = 0uL,
    toVersion: ULong? = null,
    maxVersions: UInt = 1u,
    select: RootPropRefGraph<DM>? = null,
    filterSoftDeleted: Boolean = true
) =
    GetUpdatesRequest(
        this,
        keys.toList(),
        where,
        fromVersion,
        toVersion,
        maxVersions,
        select,
        filterSoftDeleted
    )

/**
 * A Request to get DataObject its versioned changes by value [keys] for specific [dataModel] of type [DM]
 * It will only fetch the changes [fromVersion] (Inclusive) until [maxVersions] (Default=1) is reached.
 * Can also contain a [where] filter, [filterSoftDeleted], [toVersion] to further limit results.
 * Only selected properties can be returned with a [select] graph
 */
data class GetUpdatesRequest<DM : IsRootDataModel> internal constructor(
    override val dataModel: DM,
    override val keys: List<Key<DM>>,
    override val where: IsFilter? = null,
    override val fromVersion: ULong = 0uL,
    override val toVersion: ULong? = null,
    override val maxVersions: UInt = 1u,
    override val select: RootPropRefGraph<DM>? = null,
    override val filterSoftDeleted: Boolean = true
) : IsGetRequest<DM, UpdatesResponse<DM>>, IsUpdatesRequest<DM, UpdatesResponse<DM>>, IsTransportableRequest<UpdatesResponse<DM>> {
    override val requestType = GetChanges
    override val responseModel = UpdatesResponse

    // Aggregations are not allowed on a get changes request
    override val aggregations: Aggregations? = null

    companion object : QueryModel<GetUpdatesRequest<*>, Companion>() {
        val from by addDataModel { it.dataModel }
        val keys by list(
            index = 2u,
            getter = GetUpdatesRequest<*>::keys,
            valueDefinition = ContextualReferenceDefinition<RequestContext>(
                contextualResolver = {
                    it?.dataModel as? IsRootDataModel ?: throw ContextNotFoundException()
                }
            )
        )
        val select by embedObject(3u, GetUpdatesRequest<*>::select, dataModel = { RootPropRefGraph })
        val where by addFilter(GetUpdatesRequest<*>::where)
        val toVersion by number(5u, GetUpdatesRequest<*>::toVersion, UInt64, required = false)
        val filterSoftDeleted by boolean(6u, GetUpdatesRequest<*>::filterSoftDeleted, default = true)
        val fromVersion by number(7u, GetUpdatesRequest<*>::fromVersion, UInt64)
        val maxVersions by number(8u, GetUpdatesRequest<*>::maxVersions, UInt32, maxValue = 1000u)

        override fun invoke(values: ObjectValues<GetUpdatesRequest<*>, Companion>) =
            GetUpdatesRequest(
                dataModel = values(from.index),
                keys = values(keys.index),
                select = values(select.index),
                where = values(where.index),
                toVersion = values(toVersion.index),
                filterSoftDeleted = values(filterSoftDeleted.index),
                fromVersion = values(fromVersion.index),
                maxVersions = values(maxVersions.index)
            )
    }
}
