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
 * @param maxVersions Max amount of versions to fetch (Default=1000)
 */
data class ScanVersionedChangesRequest<DO: Any, out DM: RootDataModel<DO>>(
        override val dataModel: DM,
        override val startKey: Key<DO>,
        override val filter: IsFilter? = null,
        override val order: Order? = null,
        override val limit: UInt32 = 100.toUInt32(),
        override val fromVersion: UInt64,
        override val toVersion: UInt64? = null,
        override val maxVersions: UInt32 = 100.toUInt32(),
        override val filterSoftDeleted: Boolean = true
) : IsScanRequest<DO, DM>, IsVersionedChangesRequest<DO, DM> {
    object Properties {
        val maxVersions = NumberDefinition(
                name = "maxVersions",
                index = 8,
                type = UInt32
        )
    }

    companion object: QueryDataModel<ScanVersionedChangesRequest<*, *>>(
            definitions = listOf(
                    Def(IsObjectRequest.Properties.dataModel, ScanVersionedChangesRequest<*, *>::dataModel),
                    Def(ScanRequest.Properties.startKey, ScanVersionedChangesRequest<*, *>::startKey),
                    Def(IsFetchRequest.Properties.filter)  {
                        it.filter?.let { TypedValue(it.filterType.index, it) }
                    },
                    Def(IsFetchRequest.Properties.order, ScanVersionedChangesRequest<*, *>::order),
                    Def(IsFetchRequest.Properties.toVersion, ScanVersionedChangesRequest<*, *>::toVersion),
                    Def(IsFetchRequest.Properties.filterSoftDeleted, ScanVersionedChangesRequest<*, *>::filterSoftDeleted),
                    Def(ScanRequest.Properties.limit, ScanVersionedChangesRequest<*, *>::limit),
                    Def(ScanChangesRequest.Properties.fromVersion, ScanVersionedChangesRequest<*, *>::fromVersion),
                    Def(Properties.maxVersions, ScanVersionedChangesRequest<*, *>::maxVersions)
            )
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = ScanVersionedChangesRequest(
                dataModel = map[0] as RootDataModel<Any>,
                startKey = map[1] as Key<Any>,
                filter = (map[2] as TypedValue<IsFilter>?)?.value,
                order = map[3] as Order?,
                toVersion = map[4] as UInt64?,
                filterSoftDeleted = map[5] as Boolean,
                limit = map[6] as UInt32,
                fromVersion = map[7] as UInt64,
                maxVersions = map[8] as UInt32
        )
    }
}