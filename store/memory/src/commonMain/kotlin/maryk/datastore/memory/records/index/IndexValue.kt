package maryk.datastore.memory.records.index

import maryk.core.clock.HLC
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.datastore.memory.records.DataRecord

/** Defines a single [record] at [version] for [value]. */
internal class IndexValue<DM : IsRootValuesDataModel<P>, P : PropertyDefinitions, T : Any>(
    override val value: T,
    override val record: DataRecord<DM, P>?,
    override val version: HLC
) : IsIndexItem<DM, P, T>, IsRecordAtVersion<DM, P>
