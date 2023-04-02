package maryk.core.models.definitions

import maryk.core.properties.IsPropertyDefinitions

/** A DataModel which holds properties and can be validated */
interface IsDataModelDefinition<DM : IsPropertyDefinitions> {
    /** Object which contains all property definitions. Can also be used to get property references. */
    val properties: DM
}
