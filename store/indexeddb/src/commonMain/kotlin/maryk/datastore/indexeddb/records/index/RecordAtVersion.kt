package maryk.datastore.indexeddb.records.index

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.datastore.indexeddb.records.DataRecord

/**  [record] at [version]. Primarily for in indexes */
internal class RecordAtVersion<DM : IsRootDataModel>(
    override val record: DataRecord<DM>?,
    override val version: HLC
) : IsRecordAtVersion<DM>
