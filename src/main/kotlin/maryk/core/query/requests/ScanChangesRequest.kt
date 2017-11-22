package maryk.core.query.requests

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.UInt64
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.query.Order
import maryk.core.query.filters.IsFilter

/** A Request to scan DataObjects by key for specific DataModel
 * @param dataModel Root model of data to retrieve objects from
 * @param startKey to start scan at (inclusive)
 * @param limit amount of items to fetch
 * @param fromVersion the version to start getting objects of (Inclusive)
 */
data class ScanChangesRequest<DO: Any, out DM: RootDataModel<DO>>(
        override val dataModel: DM,
        override val startKey: Key<DO>,
        override val filter: IsFilter? = null,
        override val order: Order? = null,
        override val limit: UInt32 = 100.toUInt32(),
        override val fromVersion: UInt64,
        override val toVersion: UInt64? = null,
        override val filterSoftDeleted: Boolean = true
) : IsScanRequest<DO, DM>, IsChangesRequest<DO, DM> {
    object Properties {
        val fromVersion = NumberDefinition(
                name = "fromVersion",
                index = 7,
                type = UInt64
        )
    }

    companion object: QueryDataModel<ScanChangesRequest<*, *>>(
            construct = {
                @Suppress("UNCHECKED_CAST")
                ScanChangesRequest(
                        dataModel = it[0] as RootDataModel<Any>,
                        startKey = it[1] as Key<Any>,
                        filter = (it[2] as TypedValue<IsFilter>?)?.value,
                        order = it[3] as Order?,
                        toVersion = it[4] as UInt64?,
                        filterSoftDeleted = it[5] as Boolean,
                        limit = it[6] as UInt32,
                        fromVersion = it[7] as UInt64
                )
            },
            definitions = listOf(
                    Def(IsObjectRequest.Properties.dataModel, ScanChangesRequest<*, *>::dataModel),
                    Def(ScanRequest.Properties.startKey, ScanChangesRequest<*, *>::startKey),
                    Def(IsFetchRequest.Properties.filter) {
                        it.filter?.let { TypedValue(it.filterType.index, it) }
                    },
                    Def(IsFetchRequest.Properties.order, ScanChangesRequest<*, *>::order),
                    Def(IsFetchRequest.Properties.toVersion, ScanChangesRequest<*, *>::toVersion),
                    Def(IsFetchRequest.Properties.filterSoftDeleted, ScanChangesRequest<*, *>::filterSoftDeleted),
                    Def(ScanRequest.Properties.limit, ScanChangesRequest<*, *>::limit),
                    Def(ScanChangesRequest.Properties.fromVersion, ScanChangesRequest<*, *>::fromVersion)
            )
    )
}
