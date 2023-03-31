package maryk.core.models

import maryk.core.properties.IsPropertyDefinitions

/** A DataModel which holds properties and can be validated */
interface IsDataModel<P : IsPropertyDefinitions> {
    /** Object which contains all property definitions. Can also be used to get property references. */
    val properties: P
}
