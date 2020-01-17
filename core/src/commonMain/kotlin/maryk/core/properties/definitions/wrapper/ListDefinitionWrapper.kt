package maryk.core.properties.definitions.wrapper

import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.graph.PropRefGraphType.PropRef
import maryk.core.properties.references.IsPropertyReference
import kotlin.reflect.KProperty

/**
 * Contains a List property [definition] which contains items of type [T]
 * It contains an [index] and [name] to which it is referred inside DataModel, and a [getter]
 * function to retrieve value on dataObject of [DO] in context [CX]
 */
data class ListDefinitionWrapper<T : Any, TO : Any, CX : IsPropertyContext, DO : Any> internal constructor(
    override val index: UInt,
    override val name: String,
    override val definition: IsListDefinition<T, CX>,
    override val alternativeNames: Set<String>? = null,
    override val getter: (DO) -> List<TO>? = { null },
    override val capturer: ((CX, List<T>) -> Unit)? = null,
    override val toSerializable: ((List<TO>?, CX?) -> List<T>?)? = null,
    override val fromSerializable: ((List<T>?) -> List<TO>?)? = null,
    override val shouldSerialize: ((Any) -> Boolean)? = null
) :
    AbstractDefinitionWrapper(index, name),
    IsListDefinition<T, CX> by definition,
    IsListDefinitionWrapper<T, TO, IsListDefinition<T, CX>, CX, DO> {
    override val graphType = PropRef

    override val anyItemRefCache =
        mutableMapOf<IsPropertyReference<*, *, *>?, IsPropertyReference<*, *, *>>()

    override val listItemRefCache =
        mutableMapOf<UInt, MutableMap<IsPropertyReference<*, *, *>?, IsPropertyReference<*, *, *>>>()

    // For delegation in definition
    operator fun getValue(thisRef: AbstractPropertyDefinitions<DO>, property: KProperty<*>) = this
}
