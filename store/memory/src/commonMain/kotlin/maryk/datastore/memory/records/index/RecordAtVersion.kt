package maryk.datastore.memory.records.index

import maryk.core.clock.HLC
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.datastore.memory.records.DataRecord

/**  [record] at [version]. Primarily for in indexes */
internal class RecordAtVersion<DM : IsRootValuesDataModel<P>, P : PropertyDefinitions>(
    override val record: DataRecord<DM, P>?,
    override val version: HLC
) : IsRecordAtVersion<DM, P>
