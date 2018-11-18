@file:Suppress("EXPERIMENTAL_API_USAGE", "EXPERIMENTAL_UNSIGNED_LITERALS", "unused")

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
import maryk.core.query.responses.VersionedChangesResponse

/**
 * Creates a request to get DataObject its versioned changes by value [keys]
 * It will only fetch the changes [fromVersion] (Inclusive) until [maxVersions] (Default=1000) is reached.
 * Can also contain a [filter], [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order]
 */
fun <DM: IsRootValuesDataModel<*>> DM.getVersionedChanges(
    vararg keys: Key<DM>,
    filter: IsFilter? = null,
    order: Order? = null,
    fromVersion: ULong,
    toVersion: ULong? = null,
    maxVersions: UInt = 1000u,
    select: RootPropRefGraph<DM>? = null,
    filterSoftDeleted: Boolean = true
) =
    GetVersionedChangesRequest(this, keys.toList(), filter, order, fromVersion, toVersion, maxVersions, select, filterSoftDeleted)

/**
 * A Request to get DataObject its versioned changes by value [keys] for specific [dataModel] of type [DM]
 * It will only fetch the changes [fromVersion] (Inclusive) until [maxVersions] (Default=1000) is reached.
 * Can also contain a [filter], [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order] and only selected properties can be returned with a [select] graph
 */
@Suppress("EXPERIMENTAL_OVERRIDE")
data class GetVersionedChangesRequest<DM: IsRootValuesDataModel<*>> internal constructor(
    override val dataModel: DM,
    override val keys: List<Key<DM>>,
    override val filter: IsFilter? = null,
    override val order: Order? = null,
    override val fromVersion: ULong,
    override val toVersion: ULong? = null,
    override val maxVersions: UInt = 1000u,
    override val select: RootPropRefGraph<DM>? = null,
    override val filterSoftDeleted: Boolean = true
) : IsGetRequest<DM, VersionedChangesResponse<DM>>, IsVersionedChangesRequest<DM, VersionedChangesResponse<DM>> {
    override val requestType = RequestType.GetVersionedChanges
    @Suppress("UNCHECKED_CAST")
    override val responseModel = VersionedChangesResponse as IsObjectDataModel<VersionedChangesResponse<DM>, *>

    @Suppress("unused")
    object Properties : ObjectPropertyDefinitions<GetVersionedChangesRequest<*>>() {
        val dataModel = IsObjectRequest.addDataModel(this, GetVersionedChangesRequest<*>::dataModel)
        val keys = IsGetRequest.addKeys(this, GetVersionedChangesRequest<*>::keys)
        val select = IsFetchRequest.addSelect(this, GetVersionedChangesRequest<*>::select)
        val filter = IsFetchRequest.addFilter(this,  GetVersionedChangesRequest<*>::filter)
        val order = IsFetchRequest.addOrder(this, GetVersionedChangesRequest<*>::order)
        val toVersion = IsFetchRequest.addToVersion(this, GetVersionedChangesRequest<*>::toVersion)
        val filterSoftDeleted = IsFetchRequest.addFilterSoftDeleted(this, GetVersionedChangesRequest<*>::filterSoftDeleted)
        val fromVersion = IsChangesRequest.addFromVersion(8, this, GetVersionedChangesRequest<*>::fromVersion)
        val maxVersions = IsVersionedChangesRequest.addMaxVersions(9, this, GetVersionedChangesRequest<*>::maxVersions)
    }

    companion object: QueryDataModel<GetVersionedChangesRequest<*>, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<GetVersionedChangesRequest<*>, Properties>) = GetVersionedChangesRequest(
            dataModel = values(1),
            keys = values(2),
            select = values(3),
            filter = values(4),
            order = values(5),
            toVersion = values(6),
            filterSoftDeleted = values(7),
            fromVersion = values(8),
            maxVersions = values(9)
        )
    }
}
