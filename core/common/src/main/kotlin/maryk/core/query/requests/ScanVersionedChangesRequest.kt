@file:Suppress("EXPERIMENTAL_API_USAGE", "EXPERIMENTAL_UNSIGNED_LITERALS", "unused")

package maryk.core.query.requests

import maryk.core.models.IsRootDataModel
import maryk.core.models.QueryDataModel
import maryk.core.objects.ObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.Key
import maryk.core.query.Order
import maryk.core.query.filters.IsFilter
import maryk.core.query.responses.VersionedChangesResponse

/**
 * Creates a request to scan DataObjects by key from [startKey] until [limit]
 * It will only fetch the changes [fromVersion] (Inclusive) until [maxVersions] (Default=1000) is reached.
 * Can also contain a [filter], [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order]
 */
fun <DM: IsRootDataModel<*>> DM.scanVersionedChanges(
    startKey: Key<DM>,
    filter: IsFilter? = null,
    order: Order? = null,
    limit: UInt = 100u,
    fromVersion: ULong,
    toVersion: ULong? = null,
    maxVersions: UInt = 1000u,
    select: RootPropRefGraph<DM>? = null,
    filterSoftDeleted: Boolean = true
) =
    ScanVersionedChangesRequest(this, startKey, filter, order, limit, fromVersion, toVersion, maxVersions, select, filterSoftDeleted)

/**
 * A Request to scan DataObjects by key from [startKey] until [limit] for specific [dataModel]
 * It will only fetch the changes [fromVersion] (Inclusive) until [maxVersions] (Default=1000) is reached.
 * Can also contain a [filter], [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order] and only selected properties can be returned with a [select] graph
 */
@Suppress("EXPERIMENTAL_OVERRIDE")
data class ScanVersionedChangesRequest<DM: IsRootDataModel<*>> internal constructor(
    override val dataModel: DM,
    override val startKey: Key<DM>,
    override val filter: IsFilter? = null,
    override val order: Order? = null,
    override val limit: UInt = 100u,
    override val fromVersion: ULong,
    override val toVersion: ULong? = null,
    override val maxVersions: UInt = 1000u,
    override val select: RootPropRefGraph<DM>? = null,
    override val filterSoftDeleted: Boolean = true
) : IsScanRequest<DM, VersionedChangesResponse<*>>, IsVersionedChangesRequest<DM, VersionedChangesResponse<*>> {
    override val requestType = RequestType.ScanVersionedChanges
    override val responseModel = VersionedChangesResponse

    @Suppress("unused")
    object Properties : ObjectPropertyDefinitions<ScanVersionedChangesRequest<*>>() {
        val dataModel = IsObjectRequest.addDataModel(this, ScanVersionedChangesRequest<*>::dataModel)
        val startKey = IsScanRequest.addStartKey(this, ScanVersionedChangesRequest<*>::startKey)
        val select = IsFetchRequest.addSelect(this, ScanVersionedChangesRequest<*>::select)
        val filter = IsFetchRequest.addFilter(this, ScanVersionedChangesRequest<*>::filter)
        val order = IsFetchRequest.addOrder(this, ScanVersionedChangesRequest<*>::order)
        val toVersion = IsFetchRequest.addToVersion(this, ScanVersionedChangesRequest<*>::toVersion)
        val filterSoftDeleted = IsFetchRequest.addFilterSoftDeleted(this, ScanVersionedChangesRequest<*>::filterSoftDeleted)
        val limit = IsScanRequest.addLimit(this, ScanVersionedChangesRequest<*>::limit)
        val fromVersion = IsChangesRequest.addFromVersion(9, this, ScanVersionedChangesRequest<*>::fromVersion)
        val maxVersions = IsVersionedChangesRequest.addMaxVersions(10, this, ScanVersionedChangesRequest<*>::maxVersions)
    }

    companion object: QueryDataModel<ScanVersionedChangesRequest<*>, Properties>(
        properties = Properties
    ) {
        override fun invoke(map: ObjectValues<ScanVersionedChangesRequest<*>, Properties>) = ScanVersionedChangesRequest(
            dataModel = map(1),
            startKey = map(2),
            select = map(3),
            filter = map(4),
            order = map(5),
            toVersion = map(6),
            filterSoftDeleted = map(7),
            limit = map(8),
            fromVersion = map(9),
            maxVersions = map(10)
        )
    }
}
