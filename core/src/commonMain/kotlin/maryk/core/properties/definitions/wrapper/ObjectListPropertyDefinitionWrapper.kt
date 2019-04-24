package maryk.core.properties.definitions.wrapper

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.graph.PropRefGraphType.PropRef
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.AnySpecificWrappedPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.values.AbstractValues

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
    override val index: UInt,
    override val name: String,
    val properties: P,
    override val definition: ListDefinition<ODO, CX>,
    override val getter: (DO) -> List<TO>? = { null },
    override val capturer: ((CX, List<ODO>) -> Unit)? = null,
    override val toSerializable: ((List<TO>?, CX?) -> List<ODO>?)? = null,
    override val fromSerializable: ((List<ODO>?) -> List<TO>?)? = null,
    override val shouldSerialize: ((Any) -> Boolean)? = null
) :
    AbstractPropertyDefinitionWrapper(index, name),
    IsListDefinition<ODO, CX> by definition,
    IsListPropertyDefinitionWrapper<ODO, TO, ListDefinition<ODO, CX>, CX, DO> {
    override val graphType = PropRef

    /** Get sub reference below an index */
    @Suppress("UNCHECKED_CAST")
    operator fun get(index: UInt): (
        (P.() -> (AnyOutPropertyReference?) -> AnySpecificWrappedPropertyReference) ->
            (AnyOutPropertyReference?) -> AnySpecificWrappedPropertyReference
    ) {
        val objectValuesDefinition = this.definition.valueDefinition as EmbeddedObjectDefinition<ODO, P, *, *, *>

        return { referenceGetter ->
            { parentRef ->
                objectValuesDefinition.dataModel(
                    this.itemRef(index, this.ref(parentRef)),
                    referenceGetter as ObjectPropertyDefinitions<*>.() -> (AnyOutPropertyReference?) -> IsPropertyReference<Any, IsPropertyDefinitionWrapper<Any, *, *, *>, *>
                )
            }
        }
    }

    /** Get a top level reference on a model at [index] with [propertyDefinitionGetter] */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> refAt(
        index: UInt,
        propertyDefinitionGetter: P.() -> IsPropertyDefinitionWrapper<T, *, *, *>
    ): (AnyOutPropertyReference?) -> IsPropertyReference<T, IsPropertyDefinitionWrapper<T, *, *, *>, *> {
        val objectValuesDefinition = this.definition.valueDefinition as EmbeddedObjectDefinition<ODO, P, *, *, *>

        return {
            objectValuesDefinition.dataModel.ref(
                this.getItemRef(index, it),
                propertyDefinitionGetter as ObjectPropertyDefinitions<*>.() -> IsPropertyDefinitionWrapper<T, *, *, AbstractValues<*, *, *>>
            )
        }
    }

    /** Reference values to references from [referenceGetter] at given [index] of list */
    fun <T : Any, W : IsPropertyDefinitionWrapper<T, *, *, *>, R : IsPropertyReference<T, W, *>> at(
        index: UInt,
        referenceGetter: P.() -> (AnyOutPropertyReference?) -> R
    ): (AnyOutPropertyReference?) -> R =
        @Suppress("UNCHECKED_CAST")
        {
            val objectValuesDefinition = this.definition.valueDefinition as EmbeddedObjectDefinition<ODO, P, *, *, *>

            objectValuesDefinition.dataModel(
                this.getItemRef(index, it),
                referenceGetter as ObjectPropertyDefinitions<*>.() -> (AnyOutPropertyReference?) -> R
            )
        }

    /** Reference values to references from [referenceGetter] at any item of list */
    fun <T : Any, W : IsPropertyDefinitionWrapper<T, *, *, *>, R : IsPropertyReference<T, W, *>> atAny(
        referenceGetter: P.() -> (AnyOutPropertyReference?) -> R
    ): (AnyOutPropertyReference?) -> R =
        @Suppress("UNCHECKED_CAST")
        {
            val objectValuesDefinition = this.definition.valueDefinition as EmbeddedObjectDefinition<ODO, P, *, *, *>

            objectValuesDefinition.dataModel(
                this.getAnyItemRef(it),
                referenceGetter as ObjectPropertyDefinitions<*>.() -> (AnyOutPropertyReference?) -> R
            )
        }
}
