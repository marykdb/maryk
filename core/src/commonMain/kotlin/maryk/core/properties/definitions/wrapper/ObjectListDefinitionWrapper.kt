package maryk.core.properties.definitions.wrapper

import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsTypedObjectDataModel
import maryk.core.models.invoke
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.graph.PropRefGraphType.PropRef
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.AnySpecificWrappedPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ListAnyValuePropertyReference

/**
 * Contains a List property [definition] which contains values of type [ODO] and [DM]
 * It contains an [index] and [name] to which it is referred inside DataModel, and a [getter]
 * function to retrieve value on dataObject of [DO] in context [CX]
 */
data class ObjectListDefinitionWrapper<
    ODO : Any,
    DM : IsTypedObjectDataModel<ODO, *, CX, CX>,
    TO : Any,
    CX : IsPropertyContext,
    in DO : Any
> internal constructor(
    override val index: UInt,
    override val name: String,
    val properties: DM,
    override val definition: ListDefinition<ODO, CX>,
    override val alternativeNames: Set<String>? = null,
    override val getter: (DO) -> List<TO>? = { null },
    override val capturer: ((CX, List<ODO>) -> Unit)? = null,
    override val toSerializable: ((List<TO>?, CX?) -> List<ODO>?)? = null,
    override val fromSerializable: ((List<ODO>?) -> List<TO>?)? = null,
    override val shouldSerialize: ((Any) -> Boolean)? = null
) :
    AbstractDefinitionWrapper(index, name),
    IsListDefinition<ODO, CX> by definition,
    IsListDefinitionWrapper<ODO, TO, ListDefinition<ODO, CX>, CX, DO> {
    override val graphType = PropRef

    /** Get sub reference below an index */
    @Suppress("UNCHECKED_CAST")
    operator fun get(index: UInt): (
        (DM.() -> (AnyOutPropertyReference?) -> AnySpecificWrappedPropertyReference) ->
            (AnyOutPropertyReference?) -> AnySpecificWrappedPropertyReference
    ) {
        val objectValuesDefinition = this.definition.valueDefinition as EmbeddedObjectDefinition<ODO, DM, *, *>

        return { referenceGetter ->
            { parentRef ->
                objectValuesDefinition.dataModel(
                    this.definition.itemRef(index, this.ref(parentRef)),
                    referenceGetter as IsObjectDataModel<*>.() -> (AnyOutPropertyReference?) -> IsPropertyReference<Any, IsDefinitionWrapper<Any, *, *, *>, *>
                )
            }
        }
    }

    /** Get a top level reference on a model at [index] with [propertyDefinitionGetter] */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> refAt(
        index: UInt,
        propertyDefinitionGetter: DM.() -> IsDefinitionWrapper<T, *, *, *>
    ): (AnyOutPropertyReference?) -> IsPropertyReference<T, IsDefinitionWrapper<T, *, *, *>, *> {
        val objectValuesDefinition = this.definition.valueDefinition as EmbeddedObjectDefinition<ODO, DM, *, *>

        return {
            propertyDefinitionGetter(
                objectValuesDefinition.dataModel
            ).ref(this.getItemRef(index,it)) as IsPropertyReference<T, IsDefinitionWrapper<T, *, *, *>, *>
        }
    }

    /** Reference values to references from [referenceGetter] at given [index] of list */
    fun <T : Any, W : IsDefinitionWrapper<T, *, *, *>, R : IsPropertyReference<T, W, *>> at(
        index: UInt,
        referenceGetter: DM.() -> (AnyOutPropertyReference?) -> R
    ): (AnyOutPropertyReference?) -> R =
        @Suppress("UNCHECKED_CAST")
        {
            val objectValuesDefinition = this.definition.valueDefinition as EmbeddedObjectDefinition<ODO, DM, *, *>

            objectValuesDefinition.dataModel(
                this.getItemRef(index, it),
                referenceGetter as IsObjectDataModel<*>.() -> (AnyOutPropertyReference?) -> R
            )
        }

    /** Reference values to references from [referenceGetter] at any item of list */
    fun <T : Any, W : IsDefinitionWrapper<T, *, *, *>, R : IsPropertyReference<T, W, *>> atAny(
        referenceGetter: DM.() -> (AnyOutPropertyReference?) -> R
    ): (AnyOutPropertyReference?) -> IsPropertyReference<List<T>, IsListDefinition<T, IsPropertyContext>, *> =
        @Suppress("UNCHECKED_CAST")
        {
            val objectValuesDefinition = this.definition.valueDefinition as EmbeddedObjectDefinition<ODO, DM, *, *>

            ListAnyValuePropertyReference(
                objectValuesDefinition.dataModel(
                    this.getAnyItemRef(it),
                    referenceGetter as IsObjectDataModel<*>.() -> (AnyOutPropertyReference?) -> R
                )
            )
        }
}
