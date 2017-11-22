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
data class ScanRequest<DO: Any, out DM: RootDataModel<DO>>(
        override val dataModel: DM,
        override val startKey: Key<DO>,
        override val filter: IsFilter? = null,
        override val order: Order? = null,
        override val limit: UInt32 = 100.toUInt32(),
        override val toVersion: UInt64? = null,
        override val filterSoftDeleted: Boolean = true
) : IsScanRequest<DO, DM> {
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
                    Def(IsObjectRequest.Properties.dataModel, ScanRequest<*, *>::dataModel),
                    Def(ScanRequest.Properties.startKey, ScanRequest<*, *>::startKey),
                    Def(IsFetchRequest.Properties.filter)  {
                        it.filter?.let { TypedValue(it.filterType.index, it) }
                    },
                    Def(IsFetchRequest.Properties.order, ScanRequest<*, *>::order),
                    Def(IsFetchRequest.Properties.toVersion, ScanRequest<*, *>::toVersion),
                    Def(IsFetchRequest.Properties.filterSoftDeleted, ScanRequest<*, *>::filterSoftDeleted),
                    Def(ScanRequest.Properties.limit, ScanRequest<*, *>::limit)
            )
    )
}