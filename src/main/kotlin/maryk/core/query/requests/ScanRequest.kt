package maryk.core.query.requests

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.UInt64
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.Order
import maryk.core.query.filters.IsFilter

/** A Request to scan DataObjects by key for specific DataModel
 * @param dataModel Root model of data to retrieve objects from
 * @param startKey to start scan at (inclusive)
 * @param limit amount of items to fetch
 */
open class ScanRequest<DO: Any, out DM: RootDataModel<DO>>(
        dataModel: DM,
        val startKey: Key<DO>,
        filter: IsFilter? = null,
        order: Order? = null,
        val limit: UInt32 = 100.toUInt32(),
        toVersion: UInt64? = null,
        filterSoftDeleted: Boolean = true
) : AbstractFetchRequest<DO, DM>(dataModel, filter, order, toVersion, filterSoftDeleted) {
    object Properties {
        val startKey = ContextualReferenceDefinition<DataModelPropertyContext>(
                name = "startKey",
                index = 1,
                contextualResolver = { it!!.dataModel!!.key }
        )
        val limit = NumberDefinition(
                name = "limit",
                index = 6,
                type = UInt32
        )
    }

    companion object: QueryDataModel<ScanRequest<*, *>>(
            construct = {
                @Suppress("UNCHECKED_CAST")
                ScanRequest(
                        dataModel = it[0] as RootDataModel<Any>,
                        startKey = it[1] as Key<Any>,
                        filter = (it[2] as TypedValue<IsFilter>?)?.value,
                        order = it[3] as Order?,
                        toVersion = it[4] as UInt64?,
                        filterSoftDeleted = it[5] as Boolean,
                        limit = it[6] as UInt32
                )
            },
            definitions = listOf(
                    Def(AbstractModelRequest.Properties.dataModel, ScanRequest<*, *>::dataModel),
                    Def(ScanRequest.Properties.startKey, ScanRequest<*, *>::startKey),
                    Def(AbstractFetchRequest.Properties.filter)  {
                        it.filter?.let { TypedValue(it.filterType.index, it) }
                    },
                    Def(AbstractFetchRequest.Properties.order, ScanRequest<*, *>::order),
                    Def(AbstractFetchRequest.Properties.toVersion, ScanRequest<*, *>::toVersion),
                    Def(AbstractFetchRequest.Properties.filterSoftDeleted, ScanRequest<*, *>::filterSoftDeleted),
                    Def(ScanRequest.Properties.limit, ScanRequest<*, *>::limit)
            )
    )
}