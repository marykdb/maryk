package maryk.datastore.memory.records.index

import maryk.core.clock.HLC
import maryk.core.properties.IsRootModel
import maryk.datastore.memory.records.DataRecord

/** Defines that this is an [record] reference at a certain [version] */
internal interface IsRecordAtVersion<DM : IsRootModel> {
    val record: DataRecord<DM>?
    val version: HLC
    fun recordAtVersion(version: HLC) =
        if (version >= this.version) {
            this.record
        } else null
}
