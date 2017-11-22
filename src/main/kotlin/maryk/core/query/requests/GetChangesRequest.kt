package maryk.core.query.requests

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.NumberDefinition
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
open class GetChangesRequest<DO: Any, out DM: RootDataModel<DO>>(
        dataModel: DM,
        vararg keys: Key<DO>,
        filter: IsFilter? = null,
        order: Order? = null,
        val fromVersion: UInt64,
        toVersion: UInt64? = null,
        filterSoftDeleted: Boolean = true
) : GetRequest<DO, DM>(
        dataModel,
        *keys,
        filter = filter,
        order = order,
        toVersion = toVersion,
        filterSoftDeleted = filterSoftDeleted
) {
    object Properties {
        val fromVersion = NumberDefinition(
                name = "fromVersion",
                index = 6,
                type = UInt64
        )
    }

    companion object: QueryDataModel<GetChangesRequest<*, *>>(
            construct = {
                @Suppress("UNCHECKED_CAST")
                GetChangesRequest(
                        dataModel = it[0] as RootDataModel<Any>,
                        keys = *(it[1] as List<Key<Any>>).toTypedArray(),
                        filter = (it[2] as TypedValue<IsFilter>?)?.value,
                        order = it[3] as Order?,
                        toVersion = it[4] as UInt64?,
                        filterSoftDeleted = it[5] as Boolean,
                        fromVersion = it[6] as UInt64
                )
            },
            definitions = listOf(
                    Def(AbstractModelRequest.Properties.dataModel, GetChangesRequest<*, *>::dataModel),
                    Def(GetRequest.Properties.keys, {
                        @Suppress("UNCHECKED_CAST")
                        it.keys.toList() as List<Key<Any>>
                    }),
                    Def(AbstractFetchRequest.Properties.filter)  {
                        it.filter?.let { TypedValue(it.filterType.index, it) }
                    },
                    Def(AbstractFetchRequest.Properties.order, GetChangesRequest<*, *>::order),
                    Def(AbstractFetchRequest.Properties.toVersion, GetChangesRequest<*, *>::toVersion),
                    Def(AbstractFetchRequest.Properties.filterSoftDeleted, GetChangesRequest<*, *>::filterSoftDeleted),
                    Def(GetChangesRequest.Properties.fromVersion, GetChangesRequest<*, *>::fromVersion)
            )
    )
}
