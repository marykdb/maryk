package maryk.datastore.memory.records.index

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.datastore.memory.records.DataRecord

/** Defines that this is an [record] reference at a certain [version] */
internal interface IsRecordAtVersion<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> {
    val record: DataRecord<DM, P>?
    val version: ULong
    fun recordAtVersion(version: ULong) =
        if (version >= this.version) { this.record } else null
}
