package maryk.core.properties.definitions.wrapper

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.graph.PropRefGraphType
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.SetItemReference
import maryk.core.properties.references.SetReference

/**
 * Contains a Set property [definition] containing type [T]
 * It contains an [index] and [name] to which it is referred inside DataModel and a [getter]
 * function to retrieve value on dataObject of [DO] in context [CX]
 */
data class SetPropertyDefinitionWrapper<T : Any, CX : IsPropertyContext, in DO : Any> internal constructor(
    override val index: Int,
    override val name: String,
    override val definition: SetDefinition<T, CX>,
    override val getter: (DO) -> Set<T>? = { null },
    override val capturer: ((CX, Set<T>) -> Unit)? = null,
    override val toSerializable: ((Set<T>?, CX?) -> Set<T>?)? = null,
    override val fromSerializable: ((Set<T>?) -> Set<T>?)? = null,
    override val shouldSerialize: ((Any) -> Boolean)? = null
) :
    AbstractPropertyDefinitionWrapper(index, name),
    IsSetDefinition<T, CX> by definition,
    IsPropertyDefinitionWrapper<Set<T>, Set<T>, CX, DO> {
    override val graphType = PropRefGraphType.PropRef

    override fun ref(parentRef: AnyPropertyReference?) =
        SetReference(this, parentRef as CanHaveComplexChildReference<*, *, *, *>?)

    /** Get a reference to a specific set item by [value] with optional [parentRef] */
    private fun itemRef(value: T, parentRef: AnyPropertyReference? = null) =
        this.definition.itemRef(value, this.ref(parentRef))

    /** For quick notation to get a set [item] reference */
    infix fun refAt(item: T): (IsPropertyReference<out Any, IsPropertyDefinition<*>, *>?) -> SetItemReference<T, *> {
        return { this.itemRef(item, it) }
    }
}
