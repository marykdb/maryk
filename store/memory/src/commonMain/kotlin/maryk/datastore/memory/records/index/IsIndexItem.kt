package maryk.datastore.memory.records.index

import maryk.core.models.IsRootDataModel

/** Defines that this is an item defining one unique index value and reference */
internal interface IsIndexItem<DM : IsRootDataModel, T : Any> :
    IsRecordAtVersion<DM> {
    val value: T
}

