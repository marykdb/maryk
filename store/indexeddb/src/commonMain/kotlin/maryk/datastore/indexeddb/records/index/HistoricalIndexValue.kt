package maryk.datastore.indexeddb.records.index

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel

/**  index with historical [records] for [value] */
internal class HistoricalIndexValue<DM : IsRootDataModel, T : Any>(
    override val value: T,
    val records: MutableList<IsRecordAtVersion<DM>>
) : IsIndexItem<DM, T> {
    override val version get() = records.last().version
    override val record get() = records.last().record
    override fun recordAtVersion(version: HLC) =
        records.findLast { version >= it.version }?.record
}
