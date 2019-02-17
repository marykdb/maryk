package maryk.datastore.memory.records.index

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions

/**  index with historical [records] for [value] */
internal class HistoricalIndexValue<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions, T: Any>(
    override val value: T,
    val records: MutableList<IsRecordAtVersion<DM, P>>
): IsIndexItem<DM, P, T> {
    override val version get() = records.last().version
    override val record get() = records.last().record
    override fun recordAtVersion(version: ULong) =
        records.findLast { version >= it.version }?.record
}
