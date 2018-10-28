package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext

/** Interface for a Map definition with key [K], value [V] and context [CX] */
interface IsMapDefinition<K: Any, V: Any, in CX: IsPropertyContext>
    : IsSerializablePropertyDefinition<Map<K, V>, CX>
{
    val keyDefinition: IsSimpleValueDefinition<K, CX>
    val valueDefinition: IsSubDefinition<V, CX>
}