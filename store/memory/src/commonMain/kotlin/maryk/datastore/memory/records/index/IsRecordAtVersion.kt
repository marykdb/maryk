package maryk.datastore.memory.records.index

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.properties.IsPropertyDefinitions
import maryk.datastore.memory.records.DataRecord

/** Defines that this is an [record] reference at a certain [version] */
internal interface IsRecordAtVersion<DM : IsRootDataModel<P>, P : IsPropertyDefinitions> {
    val record: DataRecord<DM, P>?
    val version: HLC
    fun recordAtVersion(version: HLC) =
        if (version >= this.version) {
            this.record
        } else null
}
