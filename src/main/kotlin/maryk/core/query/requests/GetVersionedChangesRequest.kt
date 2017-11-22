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

/** A Request to get DataObject versioned changes by key for specific DataModel
 * @param dataModel Root model of data to retrieve objects from
 * @param keys Array of keys to retrieve object of
 * @param fromVersion the version to start getting objects of (Inclusive)
 * @param maxVersions Max amount of versions to fetch (Default=1000)
 */
class GetVersionedChangesRequest<DO: Any, out DM: RootDataModel<DO>>(
        dataModel: DM,
        vararg keys: Key<DO>,
        filter: Any? = null,
        order: Order? = null,
        toVersion: UInt64? = null,
        fromVersion: UInt64,
        val maxVersions: UInt32 = 100.toUInt32(),
        filterSoftDeleted: Boolean = true
) : GetChangesRequest<DO, DM>(
        dataModel,
        *keys,
        filter = filter,
        order = order,
        toVersion = toVersion,
        filterSoftDeleted = filterSoftDeleted,
        fromVersion = fromVersion
) {
    object Properties {
        val maxVersions = NumberDefinition(
                name = "maxVersions",
                index = 7,
                type = UInt32
        )
    }

    companion object: QueryDataModel<GetVersionedChangesRequest<*, *>>(
            construct = {
                @Suppress("UNCHECKED_CAST")
                GetVersionedChangesRequest(
                        dataModel = it[0] as RootDataModel<Any>,
                        keys = *(it[1] as List<Key<Any>>).toTypedArray(),
                        toVersion = it[4] as UInt64?,
                        filterSoftDeleted = it[5] as Boolean,
                        fromVersion = it[6] as UInt64,
                        maxVersions = it[7] as UInt32
                )
            },
            definitions = listOf(
                    Def(AbstractModelRequest.Properties.dataModel, GetVersionedChangesRequest<*, *>::dataModel),
                    Def(GetRequest.Properties.keys, {
                        @Suppress("UNCHECKED_CAST")
                        it.keys.toList() as List<Key<Any>>
                    }),
                    Def(AbstractFetchRequest.Properties.toVersion, GetVersionedChangesRequest<*, *>::toVersion),
                    Def(AbstractFetchRequest.Properties.filterSoftDeleted, GetVersionedChangesRequest<*, *>::filterSoftDeleted),
                    Def(GetChangesRequest.Properties.fromVersion, GetVersionedChangesRequest<*, *>::fromVersion),
                    Def(GetVersionedChangesRequest.Properties.maxVersions, GetVersionedChangesRequest<*, *>::maxVersions)
            )
    )
}