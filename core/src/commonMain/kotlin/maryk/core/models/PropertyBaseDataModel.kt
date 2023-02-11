package maryk.core.models

import maryk.core.properties.PropertyDefinitions

class PropertyBaseDataModel<P : PropertyDefinitions>(
    reservedIndices: List<UInt>? = null,
    reservedNames: List<String>? = null,
    properties: P,
) : DataModel<PropertyBaseDataModel<P>, P>(
    reservedIndices = reservedIndices,
    reservedNames = reservedNames,
    properties = properties,
) {
    override val name: String get() = properties::class.simpleName!!
}
