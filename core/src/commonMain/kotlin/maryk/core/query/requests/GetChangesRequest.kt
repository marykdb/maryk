@file:Suppress("unused")

package maryk.core.query.requests

import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.QueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.Key
import maryk.core.query.filters.IsFilter
import maryk.core.query.responses.ChangesResponse
import maryk.core.values.ObjectValues

/**
 * Creates a request to get DataObject its versioned changes by value [keys]
 * It will only fetch the changes [fromVersion] (Inclusive) until [maxVersions] (Default=1000) is reached.
 * Can also contain a [where] filter, [filterSoftDeleted], [toVersion] to further limit results.
 */
fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> DM.getChanges(
    vararg keys: Key<DM>,
    where: IsFilter? = null,
    fromVersion: ULong = 0uL,
    toVersion: ULong? = null,
    maxVersions: UInt = 1u,
    select: RootPropRefGraph<P>? = null,
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
data class GetChangesRequest<DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> internal constructor(
    override val dataModel: DM,
    override val keys: List<Key<DM>>,
    override val where: IsFilter? = null,
    override val fromVersion: ULong = 0uL,
    override val toVersion: ULong? = null,
    override val maxVersions: UInt = 1u,
    override val select: RootPropRefGraph<P>? = null,
    override val filterSoftDeleted: Boolean = true
) : IsGetRequest<DM, P, ChangesResponse<DM>>, IsChangesRequest<DM, P, ChangesResponse<DM>> {
    override val requestType = RequestType.GetChanges
    @Suppress("UNCHECKED_CAST")
    override val responseModel = ChangesResponse as IsObjectDataModel<ChangesResponse<DM>, *>

    object Properties : ObjectPropertyDefinitions<GetChangesRequest<*, *>>() {
        val dataModel = IsObjectRequest.addDataModel("from", this, GetChangesRequest<*, *>::dataModel)
        val keys = IsGetRequest.addKeys(this, GetChangesRequest<*, *>::keys)
        val select = IsFetchRequest.addSelect(this, GetChangesRequest<*, *>::select)
        val where = IsFetchRequest.addFilter(this, GetChangesRequest<*, *>::where)
        val toVersion = IsFetchRequest.addToVersion(this, GetChangesRequest<*, *>::toVersion)
        val filterSoftDeleted = IsFetchRequest.addFilterSoftDeleted(this, GetChangesRequest<*, *>::filterSoftDeleted)
        val fromVersion = IsChangesRequest.addFromVersion(7, this, GetChangesRequest<*, *>::fromVersion)
        val maxVersions = IsChangesRequest.addMaxVersions(8, this, GetChangesRequest<*, *>::maxVersions)
    }

    companion object : QueryDataModel<GetChangesRequest<*, *>, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<GetChangesRequest<*, *>, Properties>) =
            GetChangesRequest<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>(
                dataModel = values(1),
                keys = values(2),
                select = values(3),
                where = values(4),
                toVersion = values(5),
                filterSoftDeleted = values(6),
                fromVersion = values(7),
                maxVersions = values(8)
            )
    }
}
