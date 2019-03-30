package maryk.core.query.requests

import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.QueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.Key
import maryk.core.query.filters.IsFilter
import maryk.core.query.orders.IsOrder
import maryk.core.query.responses.ValuesResponse
import maryk.core.values.ObjectValues

/**
 * Creates a Request to scan DataObjects by key from [startKey] until [limit] and only return [select]
 * values of properties.
 * Can also contain a [where] filter, [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order]
 */
fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> DM.scan(
    startKey: Key<DM>? = null,
    select: RootPropRefGraph<P>? = null,
    where: IsFilter? = null,
    order: IsOrder? = null,
    limit: UInt = 100u,
    toVersion: ULong? = null,
    filterSoftDeleted: Boolean = true
) =
    ScanRequest(this, startKey, select, where, order, limit, toVersion, filterSoftDeleted)

/**
 * A Request to scan DataObjects by key from [startKey] until [limit]
 * for specific [dataModel] and only return [select]
 * values of properties.
 * Can also contain a [where] filter, [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order]
 */
data class ScanRequest<DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> internal constructor(
    override val dataModel: DM,
    override val startKey: Key<DM>? = null,
    override val select: RootPropRefGraph<P>? = null,
    override val where: IsFilter? = null,
    override val order: IsOrder? = null,
    override val limit: UInt = 100u,
    override val toVersion: ULong? = null,
    override val filterSoftDeleted: Boolean = true
) : IsScanRequest<DM, P, ValuesResponse<DM, P>> {
    override val requestType = RequestType.Scan
    @Suppress("UNCHECKED_CAST")
    override val responseModel = ValuesResponse as IsObjectDataModel<ValuesResponse<DM, P>, *>

    @Suppress("unused")
    object Properties : ObjectPropertyDefinitions<ScanRequest<*, *>>() {
        val dataModel = IsObjectRequest.addDataModel("from", this, ScanRequest<*, *>::dataModel)
        val startKey = IsScanRequest.addStartKey(this, ScanRequest<*, *>::startKey)
        val select = IsFetchRequest.addSelect(this, ScanRequest<*, *>::select)
        val where = IsFetchRequest.addFilter(this, ScanRequest<*, *>::where)
        val toVersion = IsFetchRequest.addToVersion(this, ScanRequest<*, *>::toVersion)
        val filterSoftDeleted = IsFetchRequest.addFilterSoftDeleted(this, ScanRequest<*, *>::filterSoftDeleted)
        val order = IsScanRequest.addOrder(this, ScanRequest<*, *>::order)
        val limit = IsScanRequest.addLimit(this, ScanRequest<*, *>::limit)
    }

    companion object : QueryDataModel<ScanRequest<*, *>, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<ScanRequest<*, *>, Properties>) = ScanRequest(
            dataModel = values<IsRootValuesDataModel<PropertyDefinitions>>(1),
            startKey = values(2),
            select = values(3),
            where = values(4),
            toVersion = values(5),
            filterSoftDeleted = values(6),
            order = values(7),
            limit = values(8)
        )
    }
}
