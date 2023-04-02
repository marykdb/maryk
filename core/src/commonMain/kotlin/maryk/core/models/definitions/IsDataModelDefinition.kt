package maryk.core.models.definitions

import maryk.core.models.IsDataModel

/** A DataModel which holds properties and can be validated */
interface IsDataModelDefinition<DM : IsDataModel> {
    /** Object which contains all property definitions. Can also be used to get property references. */
    val properties: DM
}
