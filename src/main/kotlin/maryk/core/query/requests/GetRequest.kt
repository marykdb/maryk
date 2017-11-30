package maryk.core.query.requests

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.UInt64
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.Order
import maryk.core.query.filters.IsFilter

/** A Request to get DataObjects by key for specific DataModel
 * @param dataModel Root model of data to retrieve objects from
 * @param filter to use to filter data
 * @param order to use for ordering the found data
 * @param toVersion until which version to retrieve data. (exclusive)
 * @param keys Array of keys to retrieve object of
 */
data class GetRequest<DO: Any, out DM: RootDataModel<DO>>(
        override val dataModel: DM,
        override val keys: List<Key<DO>>,
        override val filter: IsFilter? = null,
        override val order: Order? = null,
        override val toVersion: UInt64? = null,
        override val filterSoftDeleted: Boolean = true
) : IsGetRequest<DO, DM> {
    constructor(
            dataModel: DM,
            vararg key: Key<DO>,
            filter: IsFilter? = null,
            order: Order? = null,
            toVersion: UInt64? = null,
            filterSoftDeleted: Boolean = true
    ) : this(dataModel, key.toList(), filter, order, toVersion, filterSoftDeleted)

    object Properties {
        val keys = ListDefinition(
                name = "keys",
                index = 1,
                required = true,
                valueDefinition = ContextualReferenceDefinition<DataModelPropertyContext>(
                        contextualResolver = { it!!.dataModel!!.key }
                )
        )
    }

    companion object: QueryDataModel<GetRequest<*, *>>(
            definitions = listOf(
                    Def(IsObjectRequest.Properties.dataModel, GetRequest<*, *>::dataModel),
                    Def(Properties.keys, GetRequest<*, *>::keys),
                    Def(IsFetchRequest.Properties.filter)  {
                        it.filter?.let { TypedValue(it.filterType.index, it) }
                    },
                    Def(IsFetchRequest.Properties.order, GetRequest<*, *>::order),
                    Def(IsFetchRequest.Properties.toVersion, GetRequest<*, *>::toVersion),
                    Def(IsFetchRequest.Properties.filterSoftDeleted, GetRequest<*, *>::filterSoftDeleted)
            )
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = GetRequest(
                dataModel = map[0] as RootDataModel<Any>,
                keys = map[1] as List<Key<Any>>,
                filter = (map[2] as TypedValue<IsFilter>?)?.value,
                order = map[3] as Order?,
                toVersion = map[4] as UInt64?,
                filterSoftDeleted = map[5] as Boolean
        )
    }
}