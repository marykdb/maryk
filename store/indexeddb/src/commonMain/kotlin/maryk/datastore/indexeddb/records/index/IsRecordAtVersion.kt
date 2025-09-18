package maryk.datastore.indexeddb.records.index

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.datastore.indexeddb.records.DataRecord

/** Defines that this is an [record] reference at a certain [version] */
internal interface IsRecordAtVersion<DM : IsRootDataModel> {
    val record: DataRecord<DM>?
    val version: HLC
    fun recordAtVersion(version: HLC) =
        if (version >= this.version) {
            this.record
        } else null
}
