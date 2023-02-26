package maryk.datastore.memory.records.index

import maryk.core.properties.IsRootModel

/** Defines that this is an item defining one unique index value and reference */
internal interface IsIndexItem<DM : IsRootModel, T : Any> :
    IsRecordAtVersion<DM> {
    val value: T
}

