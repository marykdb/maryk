package maryk.core.properties.definitions.wrapper

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.graph.PropRefGraphType

/**
 * Contains a List property [definition] which contains values of type [ODO] and [P]
 * It contains an [index] and [name] to which it is referred inside DataModel and a [getter]
 * function to retrieve value on dataObject of [DO] in context [CX]
 */
data class ObjectListPropertyDefinitionWrapper<
        ODO: Any,
        P: ObjectPropertyDefinitions<ODO>,
        TO: Any,
        CX: IsPropertyContext,
        in DO: Any
> internal constructor(
    override val index: Int,
    override val name: String,
    override val definition: ListDefinition<ODO, CX>,
    override val getter: (DO) -> List<TO>? = { null },
    override val capturer: ((CX, List<ODO>) -> Unit)? = null,
    override val toSerializable: ((List<TO>?, CX?) -> List<ODO>?)? = null,
    override val fromSerializable: ((List<ODO>?) -> List<TO>?)? = null
) :
    AbstractPropertyDefinitionWrapper(index, name),
    IsCollectionDefinition<ODO, List<ODO>, CX, IsValueDefinition<ODO, CX>> by definition,
    IsListPropertyDefinitionWrapper<ODO, TO, ListDefinition<ODO, CX>, CX, DO>
{
    override val graphType = PropRefGraphType.PropRef
}
