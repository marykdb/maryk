package maryk.core.properties.definitions.wrapper

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.graph.PropRefGraphType

/**
 * Contains a List property [definition] which contains items of type [T]
 * It contains an [index] and [name] to which it is referred inside DataModel and a [getter]
 * function to retrieve value on dataObject of [DO] in context [CX]
 */
data class ListPropertyDefinitionWrapper<T : Any, TO : Any, CX : IsPropertyContext, in DO : Any> internal constructor(
    override val index: UInt,
    override val name: String,
    override val definition: ListDefinition<T, CX>,
    override val getter: (DO) -> List<TO>? = { null },
    override val capturer: ((CX, List<T>) -> Unit)? = null,
    override val toSerializable: ((List<TO>?, CX?) -> List<T>?)? = null,
    override val fromSerializable: ((List<T>?) -> List<TO>?)? = null,
    override val shouldSerialize: ((Any) -> Boolean)? = null
) :
    AbstractPropertyDefinitionWrapper(index, name),
    IsListDefinition<T, CX> by definition,
    IsListPropertyDefinitionWrapper<T, TO, ListDefinition<T, CX>, CX, DO> {
    override val graphType = PropRefGraphType.PropRef
}
