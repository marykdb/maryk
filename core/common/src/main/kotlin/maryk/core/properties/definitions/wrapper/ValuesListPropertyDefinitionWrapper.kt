package maryk.core.properties.definitions.wrapper

import maryk.core.models.IsValuesDataModel
import maryk.core.objects.Values
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.graph.PropRefGraphType

/**
 * Contains a List property [definition] which contains values of type [DM] and [P]
 * It contains an [index] and [name] to which it is referred inside DataModel and a [getter]
 * function to retrieve value on dataObject of [DO] in context [CX]
 */
data class ValuesListPropertyDefinitionWrapper<
        DM: IsValuesDataModel<P>,
        P: PropertyDefinitions,
        TO: Any,
        CX: IsPropertyContext,
        in DO: Any
> internal constructor(
    override val index: Int,
    override val name: String,
    override val definition: ListDefinition<Values<DM, P>, CX>,
    override val getter: (DO) -> List<TO>? = { null },
    override val capturer: ((CX, List<Values<DM, P>>) -> Unit)? = null,
    override val toSerializable: ((List<TO>?, CX?) -> List<Values<DM, P>>?)? = null,
    override val fromSerializable: ((List<Values<DM, P>>?) -> List<TO>?)? = null
) :
    AbstractPropertyDefinitionWrapper(index, name),
    IsCollectionDefinition<Values<DM, P>, List<Values<DM, P>>, CX, IsValueDefinition<Values<DM, P>, CX>> by definition,
    IsListPropertyDefinitionWrapper<Values<DM, P>, TO, ListDefinition<Values<DM, P>, CX>, CX, DO>
{
    override val graphType = PropRefGraphType.PropRef
}
