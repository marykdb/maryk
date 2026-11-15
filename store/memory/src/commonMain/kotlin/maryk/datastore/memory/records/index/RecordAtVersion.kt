package maryk.datastore.memory.records.index

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.properties.types.Key
import maryk.datastore.memory.records.DataRecord

/**  [record] at [version]. Primarily for in indexes */
internal class RecordAtVersion<DM : IsRootDataModel>(
    override val record: DataRecord<DM>?,
    override val version: HLC
) : IsRecordAtVersion<DM>

/** Extra release metadata is needed only by reordered historical read views. */
internal class OwnedIndexRelease<DM : IsRootDataModel>(
    override val record: DataRecord<DM>?,
    override val version: HLC,
    override val releasedOwner: IndexOwner<DM>,
) : IsRecordAtVersion<DM>

/** Keep release identity without retaining an erased record's values. */
internal data class IndexOwner<DM : IsRootDataModel>(val key: Key<DM>, val firstVersion: HLC) {
    constructor(record: DataRecord<DM>) : this(record.key, record.firstVersion)

    fun matches(record: DataRecord<DM>?) = record?.key == key && record.firstVersion == firstVersion
}
