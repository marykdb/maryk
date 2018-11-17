package maryk.core.properties.definitions.wrapper

import maryk.core.objects.AbstractValues
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.graph.PropRefGraphType
import maryk.core.properties.references.IsPropertyReference

/**
 * Contains a List property [definition] which contains values of type [ODO] and [P]
 * It contains an [index] and [name] to which it is referred inside DataModel and a [getter]
 * function to retrieve value on dataObject of [DO] in context [CX]
 */
data class ObjectListPropertyDefinitionWrapper<
    ODO : Any,
    P : ObjectPropertyDefinitions<ODO>,
    TO : Any,
    CX : IsPropertyContext,
    in DO : Any
> internal constructor(
    override val index: Int,
    override val name: String,
    val properties: P,
    override val definition: ListDefinition<ODO, CX>,
    override val getter: (DO) -> List<TO>? = { null },
    override val capturer: ((CX, List<ODO>) -> Unit)? = null,
    override val toSerializable: ((List<TO>?, CX?) -> List<ODO>?)? = null,
    override val fromSerializable: ((List<ODO>?) -> List<TO>?)? = null
) :
    AbstractPropertyDefinitionWrapper(index, name),
    IsListDefinition<ODO, CX> by definition,
    IsListPropertyDefinitionWrapper<ODO, TO, ListDefinition<ODO, CX>, CX, DO> {
    override val graphType = PropRefGraphType.PropRef

    /** Get sub reference below an index */
    @Suppress("UNCHECKED_CAST")
    operator fun get(index: Int): (
        (P.() -> (IsPropertyReference<out Any, IsPropertyDefinition<*>, *>?) -> IsPropertyReference<Any, IsPropertyDefinitionWrapper<Any, *, *, *>, *>) -> (IsPropertyReference<out Any, IsPropertyDefinition<*>, *>?) -> IsPropertyReference<Any, IsPropertyDefinitionWrapper<Any, *, *, *>, *>
    ) {
        val objectValuesDefinition = this.definition.valueDefinition as EmbeddedObjectDefinition<ODO, P, *, *, *>

        return { referenceGetter ->
            { parentRef ->
                objectValuesDefinition.dataModel(
                    this.getItemRef(index, this.getRef(parentRef)),
                    referenceGetter as ObjectPropertyDefinitions<*>.() -> (IsPropertyReference<out Any, IsPropertyDefinition<*>, *>?) -> IsPropertyReference<Any, IsPropertyDefinitionWrapper<Any, *, *, *>, *>
                )
            }
        }
    }

    /** Get a top level reference on a model with [propertyDefinitionGetter] */
    @Suppress("UNCHECKED_CAST")
    fun <T: Any> ref(
        index: Int,
        propertyDefinitionGetter: P.()-> IsPropertyDefinitionWrapper<T, *, *, *>
    ): (IsPropertyReference<out Any, IsPropertyDefinition<*>, *>?) -> IsPropertyReference<T, IsPropertyDefinitionWrapper<T, *, *, *>, *> {
        val objectValuesDefinition = this.definition.valueDefinition as EmbeddedObjectDefinition<ODO, P, *, *, *>

        return {
            objectValuesDefinition.dataModel.ref(
                this.getItemRef(index, it),
                propertyDefinitionGetter as ObjectPropertyDefinitions<*>.() -> IsPropertyDefinitionWrapper<T, *, *, AbstractValues<*, *, *>>
            )
        }
    }
}
