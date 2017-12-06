package maryk.core.query.requests

import maryk.core.objects.QueryDataModel
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions
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

    companion object: QueryDataModel<GetVersionedChangesRequest<*, *>>(
            properties = object : PropertyDefinitions<GetVersionedChangesRequest<*, *>>() {
                init {
                    IsObjectRequest.addDataModel(this, GetVersionedChangesRequest<*, *>::dataModel)
                    IsGetRequest.addKeys(this, GetVersionedChangesRequest<*, *>::keys)
                    IsFetchRequest.addFilter(this) {
                        it.filter?.let { TypedValue(it.filterType.index, it) }
                    }
                    IsFetchRequest.addOrder(this, GetVersionedChangesRequest<*, *>::order)
                    IsFetchRequest.addToVersion(this, GetVersionedChangesRequest<*, *>::toVersion)
                    IsFetchRequest.addFilterSoftDeleted(this, GetVersionedChangesRequest<*, *>::filterSoftDeleted)
                    IsChangesRequest.addFromVersion(6, this, GetVersionedChangesRequest<*, *>::fromVersion)
                    IsVersionedChangesRequest.addMaxVersions(7, this, GetVersionedChangesRequest<*, *>::maxVersions)
                }
            }
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