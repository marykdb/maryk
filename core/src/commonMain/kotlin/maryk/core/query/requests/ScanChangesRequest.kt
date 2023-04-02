package maryk.core.query.requests

import maryk.core.aggregations.Aggregations
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.QueryModel
import maryk.core.properties.definitions.boolean
import maryk.core.properties.definitions.embedObject
import maryk.core.properties.definitions.number
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.Key
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.filters.IsFilter
import maryk.core.query.orders.IsOrder
import maryk.core.query.orders.Order
import maryk.core.query.orders.Orders
import maryk.core.query.requests.RequestType.ScanChanges
import maryk.core.query.responses.ChangesResponse
import maryk.core.values.ObjectValues

/**
 * Creates a request to scan DataObjects by key from [startKey] until [limit]
 * It will only fetch the changes [fromVersion] (Inclusive) until [maxVersions] (Default=1) is reached.
 * Can also contain a [where] filter, [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order]
 */
fun <DM : IsRootDataModel> DM.scanChanges(
    startKey: Key<DM>? = null,
    where: IsFilter? = null,
    order: IsOrder? = null,
    limit: UInt = 100u,
    includeStart: Boolean = true,
    fromVersion: ULong = 0uL,
    toVersion: ULong? = null,
    maxVersions: UInt = 1u,
    select: RootPropRefGraph<DM>? = null,
    filterSoftDeleted: Boolean = true
) =
    ScanChangesRequest(
        this,
        startKey,
        where,
        order,
        limit,
        includeStart,
        fromVersion,
        toVersion,
        maxVersions,
        select,
        filterSoftDeleted
    )

/**
 * A Request to scan DataObjects by key from [startKey] until [limit] for specific [dataModel]
 * It will only fetch the changes [fromVersion] (Inclusive) until [maxVersions] (Default=1) is reached.
 * Can also contain a [where] filter, [filterSoftDeleted], [toVersion] to further limit results.
 * Results can be ordered with an [order] and only selected properties can be returned with a [select] graph
 */
data class ScanChangesRequest<DM : IsRootDataModel> internal constructor(
    override val dataModel: DM,
    override val startKey: Key<DM>? = null,
    override val where: IsFilter? = null,
    override val order: IsOrder? = null,
    override val limit: UInt = 100u,
    override val includeStart: Boolean = true,
    override val fromVersion: ULong = 0uL,
    override val toVersion: ULong? = null,
    override val maxVersions: UInt = 1u,
    override val select: RootPropRefGraph<DM>? = null,
    override val filterSoftDeleted: Boolean = true
) : IsScanRequest<DM, ChangesResponse<DM>>, IsChangesRequest<DM, ChangesResponse<DM>>, IsTransportableRequest<ChangesResponse<DM>> {
    override val requestType = ScanChanges
    override val responseModel = ChangesResponse

    // Do checks if the scan changes has no order or where filter on mutable values
    // which can lead to unpredictable results
    init {
        // Don't allow filters with mutable values
        // Since mutable values can change the changes would not be returned leading
        // to unreliable patterns
        where?.let {
            where.singleReference { ref ->
                !ref.propertyDefinition.required || !ref.propertyDefinition.final
            }?.let {
                throw RequestException("Property `$it` is mutable which are not supported on filters in change listeners.")
            }
        }

        // Only allow orders on the default key sorting or when they are done on required and final values
        // Otherwise values could move out of range which could lead to unreliable results
        order?.let { order ->
            if (order is Orders ||
                order is Order && (
                    order.propertyReference != null && (
                        !order.propertyReference.propertyDefinition.required ||
                            !order.propertyReference.propertyDefinition.final
                    )
                )
            ) {
                throw RequestException("$order can only be done on the main index or on required and final properties")
            }
        }
    }

    // Aggregations are not allowed on a scan changes request
    override val aggregations: Aggregations? = null

    companion object : QueryModel<ScanChangesRequest<*>, Companion>() {
        val from by addDataModel { it.dataModel }
        val startKey by addStartKey(ScanChangesRequest<*>::startKey)
        val select by embedObject(3u, ScanChangesRequest<*>::select, dataModel = { RootPropRefGraph })
        val where by addFilter(ScanChangesRequest<*>::where)
        val toVersion by number(5u, ScanChangesRequest<*>::toVersion, UInt64, required = false)
        val filterSoftDeleted  by boolean(6u, ScanChangesRequest<*>::filterSoftDeleted, default = true)
        val order by addOrder(ScanChangesRequest<*>::order)
        val limit by number(9u, ScanChangesRequest<*>::limit, type = UInt32, default = 100u)
        val includeStart by boolean(10u, ScanChangesRequest<*>::includeStart, default = true)
        val fromVersion by number(11u, ScanChangesRequest<*>::fromVersion, UInt64)
        val maxVersions by number(12u, ScanChangesRequest<*>::maxVersions, UInt32, maxValue = 1u)

        override fun invoke(values: ObjectValues<ScanChangesRequest<*>, Companion>) =
            ScanChangesRequest(
                dataModel = values(1u),
                startKey = values(2u),
                select = values(3u),
                where = values(4u),
                toVersion = values(5u),
                filterSoftDeleted = values(6u),
                order = values(8u),
                limit = values(9u),
                includeStart = values(10u),
                fromVersion = values(11u),
                maxVersions = values(12u)
            )
    }
}
