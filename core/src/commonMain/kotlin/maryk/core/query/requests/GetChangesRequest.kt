@file:Suppress("unused")

package maryk.core.query.requests

import maryk.core.aggregations.Aggregations
import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.IsRootDataModel
import maryk.core.properties.IsRootModel
import maryk.core.properties.QueryModel
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
import maryk.core.query.responses.ChangesResponse
import maryk.core.values.ObjectValues

/**
 * Creates a request to get DataObject its versioned changes by value [keys]
 * It will only fetch the changes [fromVersion] (Inclusive) until [maxVersions] (Default=1000) is reached.
 * Can also contain a [where] filter, [filterSoftDeleted], [toVersion] to further limit results.
 */
fun <DM : IsRootModel> DM.getChanges(
    vararg keys: Key<DM>,
    where: IsFilter? = null,
    fromVersion: ULong = 0uL,
    toVersion: ULong? = null,
    maxVersions: UInt = 1u,
    select: RootPropRefGraph<DM>? = null,
    filterSoftDeleted: Boolean = true
) =
    GetChangesRequest(
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
 * It will only fetch the changes [fromVersion] (Inclusive) until [maxVersions] (Default=1000) is reached.
 * Can also contain a [where] filter, [filterSoftDeleted], [toVersion] to further limit results.
 * Only selected properties can be returned with a [select] graph
 */
data class GetChangesRequest<DM : IsRootModel> internal constructor(
    override val dataModel: DM,
    override val keys: List<Key<DM>>,
    override val where: IsFilter? = null,
    override val fromVersion: ULong = 0uL,
    override val toVersion: ULong? = null,
    override val maxVersions: UInt = 1u,
    override val select: RootPropRefGraph<DM>? = null,
    override val filterSoftDeleted: Boolean = true
) : IsGetRequest<DM, ChangesResponse<DM>>, IsChangesRequest<DM, ChangesResponse<DM>>, IsTransportableRequest<ChangesResponse<DM>> {
    override val requestType = GetChanges
    override val responseModel = ChangesResponse

    // Aggregations are not allowed on a get changes request
    override val aggregations: Aggregations? = null

    companion object : QueryModel<GetChangesRequest<*>, Companion>() {
        val from by addDataModel { it.dataModel }
        val keys by list(
            index = 2u,
            getter = GetChangesRequest<*>::keys,
            valueDefinition = ContextualReferenceDefinition<RequestContext>(
                contextualResolver = {
                    it?.dataModel as IsRootDataModel<*>? ?: throw ContextNotFoundException()
                }
            )
        )
        val select by embedObject(3u, GetChangesRequest<*>::select, dataModel = { RootPropRefGraph })
        val where by addFilter(GetChangesRequest<*>::where)
        val toVersion by number(5u, GetChangesRequest<*>::toVersion, UInt64, required = false)
        val filterSoftDeleted by boolean(6u, GetChangesRequest<*>::filterSoftDeleted, default = true)
        val fromVersion by number(7u, GetChangesRequest<*>::fromVersion, UInt64)
        val maxVersions by number(8u, GetChangesRequest<*>::maxVersions, UInt32, maxValue = 1u)

        override fun invoke(values: ObjectValues<GetChangesRequest<*>, Companion>) =
            GetChangesRequest(
                dataModel = values(1u),
                keys = values(2u),
                select = values(3u),
                where = values(4u),
                toVersion = values(5u),
                filterSoftDeleted = values(6u),
                fromVersion = values(7u),
                maxVersions = values(8u)
            )
    }
}
