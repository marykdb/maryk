package maryk.datastore.memory.records.index

import maryk.core.models.IsRootDataModel
import maryk.core.properties.IsPropertyDefinitions

/** Defines that this is an item defining one unique index value and reference */
internal interface IsIndexItem<DM : IsRootDataModel<P>, P : IsPropertyDefinitions, T : Any> :
    IsRecordAtVersion<DM, P> {
    val value: T
}

