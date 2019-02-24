package maryk.core.properties.definitions.wrapper

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ListItemReference
import maryk.core.properties.references.ListReference

@Suppress("UNCHECKED_CAST")
/**
 * Contains a List property [definition] which contains items of type [T]
 * It contains an [index] and [name] to which it is referred inside DataModel and a [getter]
 * function to retrieve value on dataObject of [DO] in context [CX]
 */
interface IsListPropertyDefinitionWrapper<T : Any, TO : Any, LD : ListDefinition<T, CX>, CX : IsPropertyContext, in DO : Any> :
    IsCollectionDefinition<T, List<T>, CX, IsValueDefinition<T, CX>>,
    IsPropertyDefinitionWrapper<List<T>, List<TO>, CX, DO> {
    override val definition: LD

    @Suppress("UNCHECKED_CAST")
    override fun ref(parentRef: AnyPropertyReference?) =
        ListReference(
            this as IsListPropertyDefinitionWrapper<T, Any, ListDefinition<T, CX>, CX, *>,
            parentRef as CanHaveComplexChildReference<*, *, *, *>?
        )

    /** Get a reference to a specific list item by [index] with optional [parentRef] */
    fun getItemRef(index: UInt, parentRef: AnyPropertyReference? = null) =
        this.definition.itemRef(index, this.ref(parentRef))

    /** For quick notation to get a list item reference by [index] */
    infix fun refAt(index: UInt): (IsPropertyReference<out Any, IsPropertyDefinition<*>, *>?) -> ListItemReference<T, CX> {
        return { this.getItemRef(index, it) }
    }
}
