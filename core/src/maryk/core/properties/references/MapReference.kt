package maryk.core.properties.references

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.MapDefinitionWrapper

/**
 * Reference to a map with key [K] and value [V] and context [CX]
 */
open class MapReference<K : Any, V : Any, CX : IsPropertyContext> internal constructor(
    propertyDefinition: MapDefinitionWrapper<K, V, Any, CX, *>,
    parentReference: CanHaveComplexChildReference<*, *, *, *>?
) : PropertyReferenceForValues<Map<K, V>, Any, MapDefinitionWrapper<K, V, Any, CX, *>, CanHaveComplexChildReference<*, *, *, *>>(
        propertyDefinition,
        parentReference
    ),
    IsMapReference<K, V, CX, MapDefinitionWrapper<K, V, Any, CX, *>>
