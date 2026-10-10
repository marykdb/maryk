package maryk.core.query.requests

import maryk.core.aggregations.Aggregations
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.QueryModel
import maryk.core.properties.definitions.boolean
import maryk.core.properties.definitions.embedObject
import maryk.core.properties.definitions.number
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.filters.IsFilter
import maryk.core.query.requests.RequestType.ScanUpdateHistory
import maryk.core.query.responses.UpdatesResponse
import maryk.core.values.ObjectValues

/**
 * Creates a request to scan update entries by version using the engine update history index.
 * Results are newest-first and can repeat keys.
 */
fun <DM : IsRootDataModel> DM.scanUpdateHistory(
    where: IsFilter? = null,
    limit: UInt = 100u,
    fromVersion: ULong = 0uL,
    toVersion: ULong? = null,
    select: RootPropRefGraph<DM>? = null,
    filterSoftDeleted: Boolean = true
) =
    ScanUpdateHistoryRequest(
        dataModel = this,
        where = where,
        limit = limit,
        fromVersion = fromVersion,
        toVersion = toVersion,
        select = select,
        filterSoftDeleted = filterSoftDeleted
    )

/** Continues the same history query using an opaque [cursor]. Page size may change. */
fun <DM : IsRootDataModel> DM.scanUpdateHistory(
    where: IsFilter? = null,
    limit: UInt = 100u,
    fromVersion: ULong = 0uL,
    toVersion: ULong? = null,
    select: RootPropRefGraph<DM>? = null,
    filterSoftDeleted: Boolean = true,
    cursor: UpdateHistoryCursor?,
) = ScanUpdateHistoryRequest(this, where, limit, fromVersion, toVersion, select, filterSoftDeleted, cursor)

/**
 * A request to scan update entries by version using the engine update history index.
 * Results are newest-first and can repeat keys.
 */
class ScanUpdateHistoryRequest<DM : IsRootDataModel> internal constructor(
    override val dataModel: DM,
    override val where: IsFilter? = null,
    override val limit: UInt = 100u,
    override val fromVersion: ULong = 0uL,
    override val toVersion: ULong? = null,
    override val select: RootPropRefGraph<DM>? = null,
    override val filterSoftDeleted: Boolean = true
) : IsUpdateHistoryRequest<DM>, IsTransportableRequest<UpdatesResponse<DM>> {
    override val maxVersions: UInt = 1u
    override val requestType = ScanUpdateHistory
    override val responseModel = UpdatesResponse
    override val aggregations: Aggregations? = null

    init {
        if (limit == 0u) {
            throw RequestException("Scan update history limit should be at least 1")
        }
        if (limit > MAX_SCAN_LIMIT) {
            throw RequestException("Scan update history limit $limit exceeds maximum $MAX_SCAN_LIMIT")
        }
        if (toVersion != null && fromVersion > toVersion) {
            throw RequestException("Scan update history fromVersion cannot exceed toVersion")
        }
    }

    private var continuationCursor: UpdateHistoryCursor? = null
    override val cursor: UpdateHistoryCursor? get() = continuationCursor

    // Keep the original JVM constructor, copy, copy$default and component signatures.
    internal constructor(
        dataModel: DM,
        where: IsFilter?,
        limit: UInt,
        fromVersion: ULong,
        toVersion: ULong?,
        select: RootPropRefGraph<DM>?,
        filterSoftDeleted: Boolean,
        cursor: UpdateHistoryCursor?,
    ) : this(dataModel, where, limit, fromVersion, toVersion, select, filterSoftDeleted) {
        continuationCursor = cursor
    }

    fun copy(
        dataModel: DM = this.dataModel,
        where: IsFilter? = this.where,
        limit: UInt = this.limit,
        fromVersion: ULong = this.fromVersion,
        toVersion: ULong? = this.toVersion,
        select: RootPropRefGraph<DM>? = this.select,
        filterSoftDeleted: Boolean = this.filterSoftDeleted,
    ) = ScanUpdateHistoryRequest(dataModel, where, limit, fromVersion, toVersion, select, filterSoftDeleted, cursor)

    fun copy(
        cursor: UpdateHistoryCursor?,
        dataModel: DM = this.dataModel,
        where: IsFilter? = this.where,
        limit: UInt = this.limit,
        fromVersion: ULong = this.fromVersion,
        toVersion: ULong? = this.toVersion,
        select: RootPropRefGraph<DM>? = this.select,
        filterSoftDeleted: Boolean = this.filterSoftDeleted,
    ) = ScanUpdateHistoryRequest(dataModel, where, limit, fromVersion, toVersion, select, filterSoftDeleted, cursor)

    operator fun component1() = dataModel
    operator fun component2() = where
    operator fun component3() = limit
    operator fun component4() = fromVersion
    operator fun component5() = toVersion
    operator fun component6() = select
    operator fun component7() = filterSoftDeleted

    override fun equals(other: Any?) = other is ScanUpdateHistoryRequest<*> &&
        dataModel == other.dataModel &&
        where == other.where &&
        limit == other.limit &&
        fromVersion == other.fromVersion &&
        toVersion == other.toVersion &&
        select == other.select &&
        filterSoftDeleted == other.filterSoftDeleted && cursor == other.cursor
    override fun hashCode(): Int {
        var result = dataModel.hashCode()
        result = 31 * result + (where?.hashCode() ?: 0)
        result = 31 * result + limit.hashCode()
        result = 31 * result + fromVersion.hashCode()
        result = 31 * result + (toVersion?.hashCode() ?: 0)
        result = 31 * result + (select?.hashCode() ?: 0)
        result = 31 * result + filterSoftDeleted.hashCode()
        return 31 * result + (cursor?.hashCode() ?: 0)
    }
    override fun toString() = "ScanUpdateHistoryRequest(dataModel=$dataModel, where=$where, limit=$limit, fromVersion=$fromVersion, toVersion=$toVersion, select=$select, filterSoftDeleted=$filterSoftDeleted, cursor=$cursor)"

    companion object : QueryModel<ScanUpdateHistoryRequest<*>, Companion>() {
        val from by addDataModel { it.dataModel }
        val select by embedObject(3u, ScanUpdateHistoryRequest<*>::select, dataModel = { RootPropRefGraph })
        val where by addFilter(ScanUpdateHistoryRequest<*>::where)
        val toVersion by number(5u, ScanUpdateHistoryRequest<*>::toVersion, UInt64, required = false)
        val filterSoftDeleted by boolean(6u, ScanUpdateHistoryRequest<*>::filterSoftDeleted, default = true)
        val limit by number(9u, ScanUpdateHistoryRequest<*>::limit, type = UInt32, minValue = 1u, maxValue = MAX_SCAN_LIMIT, default = 100u)
        val fromVersion by number(11u, ScanUpdateHistoryRequest<*>::fromVersion, UInt64)

        val cursor by embedObject(12u, ScanUpdateHistoryRequest<*>::cursor, dataModel = { UpdateHistoryCursor.Model })

        override fun invoke(values: ObjectValues<ScanUpdateHistoryRequest<*>, Companion>) =
            ScanUpdateHistoryRequest(
                dataModel = values(from.index),
                select = values(select.index),
                where = values(where.index),
                toVersion = values(toVersion.index),
                filterSoftDeleted = values(filterSoftDeleted.index),
                limit = values(limit.index),
                fromVersion = values(fromVersion.index),
                cursor = values(cursor.index),
            )
    }
}
