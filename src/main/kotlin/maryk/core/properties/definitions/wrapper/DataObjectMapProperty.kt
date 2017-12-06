package maryk.core.properties.definitions.wrapper

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.MapReference

data class DataObjectMapProperty<K: Any, V: Any, CX: IsPropertyContext, in DM: Any>(
        override val index: Int,
        override val name: String,
        override val property: MapDefinition<K, V, CX>,
        override val getter: (DM) -> Map<K, V>?
) :
        IsMapDefinition<K, V, CX> by property,
        IsDataObjectProperty<Map<K,V>, CX, DM>
{
    override fun getRef(parentRefFactory: () -> IsPropertyReference<*, *>?): MapReference<K, V, CX> =
            MapReference(this, parentRefFactory() as CanHaveComplexChildReference<*, *, *>?)

    /** Get a reference to a specific map key
     * @param key to get reference for
     * @param parentRefFactory (optional) factory to create parent ref
     */
    fun getKeyRef(key: K, parentRefFactory: () -> IsPropertyReference<*, *>? = { null })
            = this.property.getKeyRef(key, this.getRef(parentRefFactory))

    /** Get a reference to a specific map value by key
     * @param key to get reference to value for
     * @param parentRefFactory (optional) factory to create parent ref
     */
    fun getValueRef(key: K, parentRefFactory: () -> IsPropertyReference<*, *>? = { null })
            = this.property.getValueRef(key, this.getRef(parentRefFactory))
}