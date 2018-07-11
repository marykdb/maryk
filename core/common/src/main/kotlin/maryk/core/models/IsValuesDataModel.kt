package maryk.core.models

import maryk.core.properties.PropertyDefinitions

/** A DataModel which holds properties and can be validated */
interface IsValuesDataModel<P: PropertyDefinitions>: IsDataModel<P>
