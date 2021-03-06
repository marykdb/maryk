@file:Suppress("unused")

package maryk.core.query.requests

import maryk.core.aggregations.Aggregations
import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.IsRootDataModel
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.QueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
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
fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> DM.getUpdates(
    vararg keys: Key<DM>,
    where: IsFilter? = null,
    fromVersion: ULong = 0uL,
    toVersion: ULong? = null,
    maxVersions: UInt = 1u,
    select: RootPropRefGraph<P>? = null,
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
data class GetUpdatesRequest<DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> internal constructor(
    override val dataModel: DM,
    override val keys: List<Key<DM>>,
    override val where: IsFilter? = null,
    override val fromVersion: ULong = 0uL,
    override val toVersion: ULong? = null,
    override val maxVersions: UInt = 1u,
    override val select: RootPropRefGraph<P>? = null,
    override val filterSoftDeleted: Boolean = true
) : IsGetRequest<DM, P, UpdatesResponse<DM, P>>, IsUpdatesRequest<DM, P, UpdatesResponse<DM, P>>, IsTransportableRequest<UpdatesResponse<DM, P>> {
    override val requestType = GetChanges
    override val responseModel = UpdatesResponse

    // Aggregations are not allowed on a get changes request
    override val aggregations: Aggregations? = null

    object Properties : ObjectPropertyDefinitions<GetChangesRequest<*, *>>() {
        val from by addDataModel(GetChangesRequest<*, *>::dataModel)
        val keys by list(
            index = 2u,
            getter = GetChangesRequest<*, *>::keys,
            valueDefinition = ContextualReferenceDefinition<RequestContext>(
                contextualResolver = {
                    it?.dataModel as IsRootDataModel<*>? ?: throw ContextNotFoundException()
                }
            )
        )
        val select by embedObject(3u, GetChangesRequest<*, *>::select, dataModel = { RootPropRefGraph })
        val where by addFilter(GetChangesRequest<*, *>::where)
        val toVersion by number(5u, GetChangesRequest<*, *>::toVersion, UInt64, required = false)
        val filterSoftDeleted by boolean(6u, GetChangesRequest<*, *>::filterSoftDeleted, default = true)
        val fromVersion by number(7u, GetChangesRequest<*, *>::fromVersion, UInt64)
        val maxVersions by number(8u, GetChangesRequest<*, *>::maxVersions, UInt32, maxValue = 1000u)
    }

    companion object : QueryDataModel<GetChangesRequest<*, *>, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<GetChangesRequest<*, *>, Properties>) =
            GetChangesRequest<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>(
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
