@file:Suppress("EXPERIMENTAL_API_USAGE", "EXPERIMENTAL_UNSIGNED_LITERALS")

package maryk.core.query.requests

import maryk.core.models.IsRootDataModel
import maryk.core.models.QueryDataModel
import maryk.core.objects.ObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.Key
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.Order
import maryk.core.query.filters.IsFilter
import maryk.core.query.responses.ValuesResponse

/**
 * Creates a Request to scan DataObjects by key from [startKey] until [limit] and only return [select]
 * values of properties.
 * Can also contain a [filter], [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order]
 */
fun <DM: IsRootDataModel<*>> DM.scan(
    startKey: Key<DM>,
    select: RootPropRefGraph<DM>? = null,
    filter: IsFilter? = null,
    order: Order? = null,
    limit: UInt = 100u,
    toVersion: UInt64? = null,
    filterSoftDeleted: Boolean = true
) =
    ScanRequest(this, startKey, select, filter, order, limit, toVersion, filterSoftDeleted)

/**
 * A Request to scan DataObjects by key from [startKey] until [limit]
 * for specific [dataModel] and only return [select]
 * values of properties.
 * Can also contain a [filter], [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order]
 */
data class ScanRequest<DM: IsRootDataModel<*>> internal constructor(
    override val dataModel: DM,
    override val startKey: Key<DM>,
    override val select: RootPropRefGraph<DM>? = null,
    override val filter: IsFilter? = null,
    override val order: Order? = null,
    override val limit: UInt = 100u,
    override val toVersion: UInt64? = null,
    override val filterSoftDeleted: Boolean = true
) : IsScanRequest<DM, ValuesResponse<*, *>> {
    override val requestType = RequestType.Scan
    override val responseModel = ValuesResponse

    @Suppress("unused")
    object Properties: ObjectPropertyDefinitions<ScanRequest<*>>() {
        val dataModel = IsObjectRequest.addDataModel(this, ScanRequest<*>::dataModel)
        val startKey = IsScanRequest.addStartKey(this, ScanRequest<*>::startKey)
        val select = IsFetchRequest.addSelect(this, ScanRequest<*>::select)
        val filter = IsFetchRequest.addFilter(this, ScanRequest<*>::filter)
        val order = IsFetchRequest.addOrder(this, ScanRequest<*>::order)
        val toVersion = IsFetchRequest.addToVersion(this, ScanRequest<*>::toVersion)
        val filterSoftDeleted = IsFetchRequest.addFilterSoftDeleted(this, ScanRequest<*>::filterSoftDeleted)
        val limit = IsScanRequest.addLimit(this, ScanRequest<*>::limit)
    }

    companion object: QueryDataModel<ScanRequest<*>, Properties>(
        properties = Properties
    ) {
        override fun invoke(map: ObjectValues<ScanRequest<*>, Properties>) = ScanRequest(
            dataModel = map(1),
            startKey = map(2),
            select = map(3),
            filter = map(4),
            order = map(5),
            toVersion = map(6),
            filterSoftDeleted = map(7),
            limit = map(8)
        )
    }
}
