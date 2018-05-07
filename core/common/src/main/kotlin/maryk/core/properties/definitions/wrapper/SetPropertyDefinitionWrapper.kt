package maryk.core.properties.definitions.wrapper

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.SetItemReference
import maryk.core.properties.references.SetReference

/**
 * Contains a Set property [definition] containing type [T]
 * It contains an [index] and [name] to which it is referred inside DataModel and a [getter]
 * function to retrieve value on dataObject of [DO] in context [CX]
 */
data class SetPropertyDefinitionWrapper<T: Any, CX: IsPropertyContext, in DO: Any> internal constructor(
    override val index: Int,
    override val name: String,
    override val definition: SetDefinition<T, CX>,
    override val getter: (DO) -> Set<T>?
) :
    IsCollectionDefinition<T, Set<T>, CX, IsValueDefinition<T, CX>> by definition,
    IsPropertyDefinitionWrapper<Set<T>, Set<T>, CX, DO>
{
    override val toSerializable: (Set<T>?) -> Set<T>? = { it }
    override val fromSerializable: (Set<T>?) -> Set<T>? = { it }

    override fun getRef(parentRef: IsPropertyReference<*, *>?) =
        SetReference(this, parentRef as CanHaveComplexChildReference<*, *, *>?)

    /** Get a reference to a specific set item by [value] with optional [parentRef] */
    fun getItemRef(value: T, parentRef: IsPropertyReference<*, *>? = null) =
        this.definition.getItemRef(value, this.getRef(parentRef))

    /** For quick notation to get a set [item] reference */
    infix fun at(item: T): (IsPropertyReference<out Any, IsPropertyDefinition<*>>?) -> SetItemReference<T, *> {
        return { this.getItemRef(item, it) }
    }
}
