package maryk.core.query.requests

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.UInt64
import maryk.core.query.Order
import maryk.core.query.filters.IsFilter

/** A Request to get DataObject changes by key for specific DataModel
 * @param dataModel Root model of data to retrieve objects from
 * @param keys Array of keys to retrieve object of
 * @param fromVersion the version to start getting objects of (Inclusive)
 */
data class GetChangesRequest<DO: Any, out DM: RootDataModel<DO>>(
        override val dataModel: DM,
        override val keys: List<Key<DO>>,
        override val filter: IsFilter? = null,
        override val order: Order? = null,
        override val fromVersion: UInt64,
        override val toVersion: UInt64? = null,
        override val filterSoftDeleted: Boolean = true
) : IsGetRequest<DO, DM>, IsChangesRequest<DO, DM> {
    constructor(
            dataModel: DM,
            vararg key: Key<DO>,
            filter: IsFilter? = null,
            order: Order? = null,
            fromVersion: UInt64,
            toVersion: UInt64? = null,
            filterSoftDeleted: Boolean = true
    ) : this(dataModel, key.toList(), filter, order, fromVersion, toVersion, filterSoftDeleted)

    internal object Properties : PropertyDefinitions<GetChangesRequest<*, *>>() {
        val fromVersion = NumberDefinition(
                name = "fromVersion",
                index = 6,
                type = UInt64
        )
    }

    companion object: QueryDataModel<GetChangesRequest<*, *>>(
            definitions = listOf(
                    Def(IsObjectRequest.Properties.dataModel, GetChangesRequest<*, *>::dataModel),
                    Def(GetRequest.Properties.keys, GetChangesRequest<*, *>::keys),
                    Def(IsFetchRequest.Properties.filter)  {
                        it.filter?.let { TypedValue(it.filterType.index, it) }
                    },
                    Def(IsFetchRequest.Properties.order, GetChangesRequest<*, *>::order),
                    Def(IsFetchRequest.Properties.toVersion, GetChangesRequest<*, *>::toVersion),
                    Def(IsFetchRequest.Properties.filterSoftDeleted, GetChangesRequest<*, *>::filterSoftDeleted),
                    Def(GetChangesRequest.Properties.fromVersion, GetChangesRequest<*, *>::fromVersion)
            )
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = GetChangesRequest(
                dataModel = map[0] as RootDataModel<Any>,
                keys = map[1] as List<Key<Any>>,
                filter = (map[2] as TypedValue<IsFilter>?)?.value,
                order = map[3] as Order?,
                toVersion = map[4] as UInt64?,
                filterSoftDeleted = map[5] as Boolean,
                fromVersion = map[6] as UInt64
        )
    }
}
