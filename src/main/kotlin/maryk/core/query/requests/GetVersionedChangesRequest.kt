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

/** A Request to get DataObject versioned changes by key for specific DataModel
 * @param dataModel Root model of data to retrieve objects from
 * @param keys Array of keys to retrieve object of
 * @param fromVersion the version to start getting objects of (Inclusive)
 * @param maxVersions Max amount of versions to fetch (Default=1000)
 */
data class GetVersionedChangesRequest<DO: Any, out DM: RootDataModel<DO>>(
        override val dataModel: DM,
        override val keys: List<Key<DO>>,
        override val filter: IsFilter? = null,
        override val order: Order? = null,
        override val toVersion: UInt64? = null,
        override val fromVersion: UInt64,
        override val maxVersions: UInt32 = 100.toUInt32(),
        override val filterSoftDeleted: Boolean = true
) : IsGetRequest<DO, DM>, IsVersionedChangesRequest<DO, DM> {
    constructor(
            dataModel: DM,
            vararg key: Key<DO>,
            filter: IsFilter? = null,
            order: Order? = null,
            toVersion: UInt64? = null,
            fromVersion: UInt64,
            maxVersions: UInt32 = 100.toUInt32(),
            filterSoftDeleted: Boolean = true
    ) : this(dataModel, key.toList(), filter, order, toVersion, fromVersion, maxVersions, filterSoftDeleted)

    object Properties {
        val maxVersions = NumberDefinition(
                name = "maxVersions",
                index = 7,
                type = UInt32
        )
    }

    companion object: QueryDataModel<GetVersionedChangesRequest<*, *>>(
            definitions = listOf(
                    Def(IsObjectRequest.Properties.dataModel, GetVersionedChangesRequest<*, *>::dataModel),
                    Def(GetRequest.Properties.keys, GetVersionedChangesRequest<*, *>::keys),
                    Def(IsFetchRequest.Properties.filter)  {
                        it.filter?.let { TypedValue(it.filterType.index, it) }
                    },
                    Def(IsFetchRequest.Properties.order, GetVersionedChangesRequest<*, *>::order),
                    Def(IsFetchRequest.Properties.toVersion, GetVersionedChangesRequest<*, *>::toVersion),
                    Def(IsFetchRequest.Properties.filterSoftDeleted, GetVersionedChangesRequest<*, *>::filterSoftDeleted),
                    Def(GetChangesRequest.Properties.fromVersion, GetVersionedChangesRequest<*, *>::fromVersion),
                    Def(GetVersionedChangesRequest.Properties.maxVersions, GetVersionedChangesRequest<*, *>::maxVersions)
            )
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = GetVersionedChangesRequest(
                dataModel = map[0] as RootDataModel<Any>,
                keys = map[1] as List<Key<Any>>,
                filter = (map[2] as TypedValue<IsFilter>?)?.value,
                order = map[3] as Order?,
                toVersion = map[4] as UInt64?,
                filterSoftDeleted = map[5] as Boolean,
                fromVersion = map[6] as UInt64,
                maxVersions = map[7] as UInt32
        )
    }
}