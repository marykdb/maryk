package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext

interface IsMapDefinition<K: Any, V: Any, in CX: IsPropertyContext>
    : IsSerializablePropertyDefinition<Map<K, V>, CX>{
    val keyDefinition: IsSimpleValueDefinition<K, CX>
    val valueDefinition: IsSubDefinition<V, CX>
}