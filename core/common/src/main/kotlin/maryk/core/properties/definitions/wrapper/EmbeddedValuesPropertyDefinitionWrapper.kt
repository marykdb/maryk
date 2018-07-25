package maryk.core.properties.definitions.wrapper

import maryk.core.models.IsValuesDataModel
import maryk.core.objects.Values
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.IsEmbeddedValuesDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.graph.PropRefGraphType
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.EmbeddedValuesPropertyRef
import maryk.core.properties.references.IsPropertyReference

/**
 * Contains a Embedded Values property [definition] containing Values and Properties described by [P]
 * in a DataModel of [DM]
 * It contains an [index] and [name] to which it is referred inside DataModel
 * It has an input context of [CX]
 */
data class EmbeddedValuesPropertyDefinitionWrapper<
    DM: IsValuesDataModel<P>,
    P: PropertyDefinitions,
    CX: IsPropertyContext,
    PDM: IsValuesDataModel<P>
> internal constructor(
    override val index: Int,
    override val name: String,
    override val definition: IsEmbeddedValuesDefinition<DM, P, CX>,
    override val getter: (Any) -> Values<DM, P>? = { null },
    override val capturer: ((CX, Values<DM, P>) -> Unit)? = null,
    override val toSerializable: ((Values<DM, P>?, CX?) -> Values<DM, P>?)? = null,
    override val fromSerializable: ((Values<DM, P>?) -> Values<DM, P>?)? = null
) :
    AbstractPropertyDefinitionWrapper(index, name),
    IsEmbeddedValuesDefinition<DM, P, CX> by definition,
    IsPropertyDefinitionWrapper<Values<DM, P>, Values<DM, P>, CX, Any>
{
    override val graphType = PropRefGraphType.PropRef

    override fun getRef(parentRef: IsPropertyReference<*, *>?) =
        EmbeddedValuesPropertyRef(
            this,
            parentRef?.let {
                it as CanHaveComplexChildReference<*, *, *>
            }
        )

    /** Get a top level reference on a model with [propertyDefinitionGetter] */
    infix fun <T: Any, W: IsPropertyDefinitionWrapper<T, *, *, *>> ref(
        propertyDefinitionGetter: P.()-> W
    ): (IsPropertyReference<out Any, IsPropertyDefinition<*>>?) -> IsPropertyReference<T, W> =
        { this.definition.dataModel.ref(this.getRef(it), propertyDefinitionGetter) }

    /** For quick notation to fetch property references with [referenceGetter] within embedded object */
    operator fun <T: Any, W: IsPropertyDefinition<T>> invoke(
        referenceGetter: P.() ->
            (IsPropertyReference<out Any, IsPropertyDefinition<*>>?) ->
                IsPropertyReference<T, W>
    ): (IsPropertyReference<out Any, IsPropertyDefinition<*>>?) -> IsPropertyReference<T, W> {
        return { this.definition.dataModel(this.getRef(it), referenceGetter) }
    }

    /** For quick notation to return [T] that operates with [runner] on Properties */
    fun <T: Any> props(
        runner: P.(EmbeddedValuesPropertyDefinitionWrapper<DM, P, CX, PDM>) -> T
    ): T {
        return runner(this.definition.dataModel.properties, this)
    }
}
