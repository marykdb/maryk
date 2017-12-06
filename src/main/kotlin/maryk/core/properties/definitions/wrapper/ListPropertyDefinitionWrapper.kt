package maryk.core.properties.definitions.wrapper

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ListReference

/** Wrapper for a list definition
 * @param index: of definition to encode into protobuf
 * @param name: of definition to display in human readable format
 * @param definition: to be wrapped for DataObject
 * @param getter: to get property value on a DataObject
 *
 * @param T: value type of property for list
 * @param CX: Context type for property
 * @param DM: Type of DataModel which contains this property
 */
data class ListPropertyDefinitionWrapper<T: Any, CX: IsPropertyContext, in DM: Any>(
        override val index: Int,
        override val name: String,
        override val definition: ListDefinition<T, CX>,
        override val getter: (DM) -> List<T>?
) :
        IsCollectionDefinition<T, List<T>, CX> by definition,
        IsPropertyDefinitionWrapper<List<T>, CX, DM>
{
    override fun getRef(parentRefFactory: () -> IsPropertyReference<*, *>?) =
            ListReference(this, parentRefFactory() as CanHaveComplexChildReference<*, *, *>?)

    /** Get a reference to a specific list item by index
     * @param index to get list item reference for
     * @param parentRefFactory (optional) factory to create parent ref
     */
    fun getItemRef(index: Int, parentRefFactory: () -> IsPropertyReference<*, *>? = { null })
            = this.definition.getItemRef(index, this.getRef(parentRefFactory))
}