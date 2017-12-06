package maryk.core.properties.definitions.wrapper

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.MapReference

/** Wraps a map definition to contain the context on how it relates to DataObject
 * @param index: of definition to encode into protobuf
 * @param name: of definition to display in human readable format
 * @param definition: to be wrapped for DataObject
 * @param getter: to get property value on a DataObject
 *
 * @param K: type of key property for map
 * @param V: type of value property for map
 * @param CX: Context type for property
 * @param DM: Type of DataModel which contains this property
 */
data class MapPropertyDefinitionWrapper<K: Any, V: Any, CX: IsPropertyContext, in DM: Any>(
        override val index: Int,
        override val name: String,
        override val definition: MapDefinition<K, V, CX>,
        override val getter: (DM) -> Map<K, V>?
) :
        IsMapDefinition<K, V, CX> by definition,
        IsPropertyDefinitionWrapper<Map<K,V>, CX, DM>
{
    override fun getRef(parentRefFactory: () -> IsPropertyReference<*, *>?): MapReference<K, V, CX> =
            MapReference(this, parentRefFactory() as CanHaveComplexChildReference<*, *, *>?)

    /** Get a reference to a specific map key
     * @param key to get reference for
     * @param parentRefFactory (optional) factory to create parent ref
     */
    fun getKeyRef(key: K, parentRefFactory: () -> IsPropertyReference<*, *>? = { null })
            = this.definition.getKeyRef(key, this.getRef(parentRefFactory))

    /** Get a reference to a specific map value by key
     * @param key to get reference to value for
     * @param parentRefFactory (optional) factory to create parent ref
     */
    fun getValueRef(key: K, parentRefFactory: () -> IsPropertyReference<*, *>? = { null })
            = this.definition.getValueRef(key, this.getRef(parentRefFactory))
}