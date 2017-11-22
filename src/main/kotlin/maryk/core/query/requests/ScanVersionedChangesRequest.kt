package maryk.core.query.requests

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.types.Key
import maryk.core.properties.types.UInt64
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.query.Order

/** A Request to scan DataObjects by key for specific DataModel
 * @param dataModel Root model of data to retrieve objects from
 * @param startKey to start scan at (inclusive)
 * @param limit amount of items to fetch
 * @param fromVersion the version to start getting objects of (Inclusive)
 * @param maxVersions Max amount of versions to fetch (Default=1000)
 */
class ScanVersionedChangesRequest<DO: Any, out DM: RootDataModel<DO>>(
        dataModel: DM,
        startKey: Key<DO>,
        filter: Any? = null,
        order: Order? = null,
        limit: UInt32 = 100.toUInt32(),
        fromVersion: UInt64,
        toVersion: UInt64? = null,
        val maxVersions: UInt32 = 100.toUInt32(),
        filterSoftDeleted: Boolean = true
) : ScanChangesRequest<DO, DM>(dataModel, startKey, filter, order, limit, fromVersion, toVersion, filterSoftDeleted) {
    object Properties {
        val maxVersions = NumberDefinition(
                name = "maxVersions",
                index = 8,
                type = UInt32
        )
    }

    companion object: QueryDataModel<ScanVersionedChangesRequest<*, *>>(
            construct = {
                @Suppress("UNCHECKED_CAST")
                ScanVersionedChangesRequest(
                        dataModel = it[0] as RootDataModel<Any>,
                        startKey = it[1] as Key<Any>,
                        toVersion = it[4] as UInt64?,
                        filterSoftDeleted = it[5] as Boolean,
                        limit = it[6] as UInt32,
                        fromVersion = it[7] as UInt64,
                        maxVersions = it[8] as UInt32
                )
            },
            definitions = listOf(
                    Def(AbstractModelRequest.Properties.dataModel, ScanVersionedChangesRequest<*, *>::dataModel),
                    Def(ScanRequest.Properties.startKey, ScanVersionedChangesRequest<*, *>::startKey),
                    Def(AbstractFetchRequest.Properties.toVersion, ScanVersionedChangesRequest<*, *>::toVersion),
                    Def(AbstractFetchRequest.Properties.filterSoftDeleted, ScanVersionedChangesRequest<*, *>::filterSoftDeleted),
                    Def(ScanRequest.Properties.limit, ScanVersionedChangesRequest<*, *>::limit),
                    Def(ScanChangesRequest.Properties.fromVersion, ScanVersionedChangesRequest<*, *>::fromVersion),
                    Def(Properties.maxVersions, ScanVersionedChangesRequest<*, *>::maxVersions)
            )
    )
}