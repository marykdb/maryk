package maryk.datastore.indexeddb.records.index

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.datastore.indexeddb.records.DataRecord

/** Defines a single [record] at [version] for [value]. */
internal class IndexValue<DM : IsRootDataModel, T : Any>(
    override val value: T,
    override val record: DataRecord<DM>?,
    override val version: HLC
) : IsIndexItem<DM, T>, IsRecordAtVersion<DM>
