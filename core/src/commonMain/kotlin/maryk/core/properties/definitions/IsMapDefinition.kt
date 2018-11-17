package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.references.MapKeyReference
import maryk.core.properties.references.MapReference
import maryk.core.properties.references.MapValueReference

/** Interface for a Map definition with key [K], value [V] and context [CX] */
interface IsMapDefinition<K: Any, V: Any, CX: IsPropertyContext>
    : IsSerializablePropertyDefinition<Map<K, V>, CX>
{
    val keyDefinition: IsSimpleValueDefinition<K, CX>
    val valueDefinition: IsSubDefinition<V, CX>

    /** Get a reference to a specific map [key] on [parentMap] */
    fun getKeyRef(key: K, parentMap: MapReference<K, V, CX>?) =
            MapKeyReference(key, this, parentMap)

    /** Get a reference to a specific map value on [parentMap] by [key] */
    fun getValueRef(key: K, parentMap: MapReference<K, V, CX>?) =
        MapValueReference(key, this, parentMap)
}
