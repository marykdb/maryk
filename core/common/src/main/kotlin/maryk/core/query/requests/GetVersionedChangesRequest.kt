package maryk.core.query.requests

import maryk.core.models.IsRootDataModel
import maryk.core.models.QueryDataModel
import maryk.core.objects.ObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.Key
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.UInt64
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.query.Order
import maryk.core.query.filters.IsFilter

/**
 * Creates a request to get DataObject its versioned changes by value [keys]
 * It will only fetch the changes [fromVersion] (Inclusive) until [maxVersions] (Default=1000) is reached.
 * Can also contain a [filter], [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order]
 */
fun <DM: IsRootDataModel<*>> DM.getVersionedChanges(
    vararg keys: Key<DM>,
    filter: IsFilter? = null,
    order: Order? = null,
    fromVersion: UInt64,
    toVersion: UInt64? = null,
    maxVersions: UInt32 = 1000.toUInt32(),
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
data class GetVersionedChangesRequest<DM: IsRootDataModel<*>> internal constructor(
    override val dataModel: DM,
    override val keys: List<Key<DM>>,
    override val filter: IsFilter? = null,
    override val order: Order? = null,
    override val fromVersion: UInt64,
    override val toVersion: UInt64? = null,
    override val maxVersions: UInt32 = 1000.toUInt32(),
    override val select: RootPropRefGraph<DM>? = null,
    override val filterSoftDeleted: Boolean = true
) : IsGetRequest<DM>, IsVersionedChangesRequest<DM> {
    override val requestType = RequestType.GetVersionedChanges

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
        override fun invoke(map: ObjectValues<GetVersionedChangesRequest<*>, Properties>) = GetVersionedChangesRequest(
            dataModel = map(1),
            keys = map(2),
            select = map(3),
            filter = map(4),
            order = map(5),
            toVersion = map(6),
            filterSoftDeleted = map(7),
            fromVersion = map(8),
            maxVersions = map(9)
        )
    }
}
