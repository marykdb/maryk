package maryk.core.properties.definitions.wrapper

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ListItemReference
import maryk.core.properties.references.ListReference

/**
 * Contains a List property [definition] which contains items of type [T]
 * It contains an [index] and [name] to which it is referred inside DataModel and a [getter]
 * function to retrieve value on dataObject of [DO] in context [CX]
 */
data class ListPropertyDefinitionWrapper<T: Any, CX: IsPropertyContext, in DO: Any>(
    override val index: Int,
    override val name: String,
    override val definition: ListDefinition<T, CX>,
    override val getter: (DO) -> List<T>?
) :
    IsCollectionDefinition<T, List<T>, CX, IsValueDefinition<T, CX>> by definition,
    IsPropertyDefinitionWrapper<List<T>, CX, DO>
{
    override fun getRef(parentRef: IsPropertyReference<*, *>?) =
        ListReference(this, parentRef as CanHaveComplexChildReference<*, *, *>?)

    /** Get a reference to a specific list item by [index] with optional [parentRef] */
    fun getItemRef(index: Int, parentRef: IsPropertyReference<*, *>? = null) =
        this.definition.getItemRef(index, this.getRef(parentRef))

    /** For quick notation to get a list item reference by [index] */
    infix fun at(index: Int): (IsPropertyReference<out Any, IsPropertyDefinition<*>>?) -> ListItemReference<T, CX> {
        return { this.getItemRef(index, it) }
    }
}