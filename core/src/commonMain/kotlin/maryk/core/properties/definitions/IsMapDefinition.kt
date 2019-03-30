package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.MapAnyValueReference
import maryk.core.properties.references.MapKeyReference
import maryk.core.properties.references.MapReference
import maryk.core.properties.references.MapValueReference

/** Interface for a Map definition with key [K], value [V] and context [CX] */
interface IsMapDefinition<K : Any, V : Any, CX : IsPropertyContext>
    : IsSerializablePropertyDefinition<Map<K, V>, CX>, IsChangeableValueDefinition<Map<K, V>, CX> {
    val keyDefinition: IsSimpleValueDefinition<K, CX>
    val valueDefinition: IsSubDefinition<V, CX>

    /** Validates size of map and throws exception if it fails */
    fun validateSize(
        mapSize: UInt,
        refGetter: () -> IsPropertyReference<Map<K, V>, IsPropertyDefinition<Map<K, V>>, *>?
    )

    /** Get a reference to a specific map [key] on [parentMap] */
    fun keyRef(key: K, parentMap: MapReference<K, V, CX>?) =
        MapKeyReference(key, this, parentMap)

    /** Get a reference to a specific map value on [parentMap] by [key] */
    fun valueRef(key: K, parentMap: MapReference<K, V, CX>?) =
        MapValueReference(key, this, parentMap)

    /** Get a reference to any map value on [parentMap] */
    fun anyValueRef(parentMap: MapReference<K, V, CX>?) =
        MapAnyValueReference(this, parentMap)
}
