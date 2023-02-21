package maryk.datastore.memory.records.index

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.properties.IsPropertyDefinitions
import maryk.datastore.memory.records.DataRecord

/** Defines a single [record] at [version] for [value]. */
internal class IndexValue<DM : IsRootDataModel<P>, P : IsPropertyDefinitions, T : Any>(
    override val value: T,
    override val record: DataRecord<DM, P>?,
    override val version: HLC
) : IsIndexItem<DM, P, T>, IsRecordAtVersion<DM, P>
