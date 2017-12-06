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
    companion object: QueryDataModel<ScanRequest<*, *>>(
            properties = object : PropertyDefinitions<ScanRequest<*, *>>() {
                init {
                    IsObjectRequest.addDataModel(this, ScanRequest<*, *>::dataModel)
                    IsScanRequest.addStartKey(this, ScanRequest<*, *>::startKey)
                    IsFetchRequest.addFilter(this) {
                        it.filter?.let { TypedValue(it.filterType.index, it) }
                    }
                    IsFetchRequest.addOrder(this, ScanRequest<*, *>::order)
                    IsFetchRequest.addToVersion(this, ScanRequest<*, *>::toVersion)
                    IsFetchRequest.addFilterSoftDeleted(this, ScanRequest<*, *>::filterSoftDeleted)
                    IsScanRequest.addLimit(this, ScanRequest<*, *>::limit)
                }
            }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = ScanRequest(
                dataModel = map[0] as RootDataModel<Any>,
                startKey = map[1] as Key<Any>,
                filter = (map[2] as TypedValue<IsFilter>?)?.value,
                order = map[3] as Order?,
                toVersion = map[4] as UInt64?,
                filterSoftDeleted = map[5] as Boolean,
                limit = map[6] as UInt32
        )
    }
}