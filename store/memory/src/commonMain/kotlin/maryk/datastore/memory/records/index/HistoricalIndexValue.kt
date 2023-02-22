package maryk.datastore.memory.records.index

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.properties.IsValuesPropertyDefinitions

/**  index with historical [records] for [value] */
internal class HistoricalIndexValue<DM : IsRootDataModel<P>, P : IsValuesPropertyDefinitions, T : Any>(
    override val value: T,
    val records: MutableList<IsRecordAtVersion<DM, P>>
) : IsIndexItem<DM, P, T> {
    override val version get() = records.last().version
    override val record get() = records.last().record
    override fun recordAtVersion(version: HLC) =
        records.findLast { version >= it.version }?.record
}
