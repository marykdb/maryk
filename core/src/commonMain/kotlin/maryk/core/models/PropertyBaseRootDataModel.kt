package maryk.core.models

import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.UUIDKey
import maryk.core.properties.types.Version

class PropertyBaseRootDataModel<P : PropertyDefinitions>(
    keyDefinition: IsIndexable = UUIDKey,
    version: Version = Version(1),
    indices: List<IsIndexable>? = null,
    reservedIndices: List<UInt>? = null,
    reservedNames: List<String>? = null,
    properties: P,
    override val name: String = properties::class.simpleName!!,
) : RootDataModel<PropertyBaseRootDataModel<P>, P>(
    keyDefinition = keyDefinition,
    version = version,
    indices = indices,
    reservedIndices = reservedIndices,
    reservedNames = reservedNames,
    properties = properties,
)
