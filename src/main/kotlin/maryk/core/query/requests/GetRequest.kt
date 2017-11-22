package maryk.core.query.requests

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.types.Key
import maryk.core.properties.types.UInt64
import maryk.core.query.Order
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.query.properties.DataModelPropertyContext

/** A Request to get DataObjects by key for specific DataModel
 * @param dataModel Root model of data to retrieve objects from
 * @param filter to use to filter data
 * @param order to use for ordering the found data
 * @param toVersion until which version to retrieve data. (exclusive)
 * @param keys Array of keys to retrieve object of
 */
open class GetRequest<DO: Any, out DM: RootDataModel<DO>>(
        dataModel: DM,
        vararg val keys: Key<DO>,
        filter: Any? = null,
        order: Order? = null,
        toVersion: UInt64? = null,
        filterSoftDeleted: Boolean = true
) : AbstractFetchRequest<DO, DM>(dataModel, filter, order, toVersion, filterSoftDeleted)  {
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
            construct = {
                @Suppress("UNCHECKED_CAST")
                GetRequest(
                        dataModel = it[0] as RootDataModel<Any>,
                        keys = *(it[1] as List<Key<Any>>).toTypedArray(),
                        toVersion = it[4] as UInt64?,
                        filterSoftDeleted = it[5] as Boolean
                )
            },
            definitions = listOf(
                    Def(AbstractModelRequest.Properties.dataModel, GetRequest<*, *>::dataModel),
                    Def(Properties.keys, {
                        @Suppress("UNCHECKED_CAST")
                        it.keys.toList() as List<Key<Any>>
                    }),
                    Def(AbstractFetchRequest.Properties.toVersion, GetRequest<*, *>::toVersion),
                    Def(AbstractFetchRequest.Properties.filterSoftDeleted, GetRequest<*, *>::filterSoftDeleted)
            )
    )
}