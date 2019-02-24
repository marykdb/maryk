package maryk.datastore.memory.records.index

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions

/** Defines that this is an item defining one unique index value and reference */
internal interface IsIndexItem<DM : IsRootValuesDataModel<P>, P : PropertyDefinitions, T : Any> :
    IsRecordAtVersion<DM, P> {
    val value: T
}

