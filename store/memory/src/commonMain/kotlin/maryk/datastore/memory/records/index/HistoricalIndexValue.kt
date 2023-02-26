package maryk.datastore.memory.records.index

import maryk.core.clock.HLC
import maryk.core.properties.IsRootModel

/**  index with historical [records] for [value] */
internal class HistoricalIndexValue<DM : IsRootModel, T : Any>(
    override val value: T,
    val records: MutableList<IsRecordAtVersion<DM>>
) : IsIndexItem<DM, T> {
    override val version get() = records.last().version
    override val record get() = records.last().record
    override fun recordAtVersion(version: HLC) =
        records.findLast { version >= it.version }?.record
}
