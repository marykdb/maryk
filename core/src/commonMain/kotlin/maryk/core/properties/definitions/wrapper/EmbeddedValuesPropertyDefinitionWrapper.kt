package maryk.core.properties.definitions.wrapper

import maryk.core.models.IsDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.IsEmbeddedValuesDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.graph.PropRefGraphType
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.EmbeddedValuesPropertyRef
import maryk.core.properties.references.IsPropertyReference
import maryk.core.values.IsValues
import maryk.core.values.Values

/**
 * Contains a Embedded Values property [definition] containing Values and Properties described by [P]
 * in a DataModel of [DM]
 * It contains an [index] and [name] to which it is referred inside DataModel
 * It has an input context of [CX]
 */
data class EmbeddedValuesPropertyDefinitionWrapper<
    DM : IsValuesDataModel<P>,
    P : PropertyDefinitions,
    CX : IsPropertyContext
> internal constructor(
    override val index: UInt,
    override val name: String,
    override val definition: IsEmbeddedValuesDefinition<DM, P, CX>,
    override val getter: (Any) -> Values<DM, P>? = { null },
    override val capturer: ((CX, Values<DM, P>) -> Unit)? = null,
    override val toSerializable: ((Values<DM, P>?, CX?) -> Values<DM, P>?)? = null,
    override val fromSerializable: ((Values<DM, P>?) -> Values<DM, P>?)? = null,
    override val shouldSerialize: ((Any) -> Boolean)? = null
) :
    AbstractPropertyDefinitionWrapper(index, name),
    IsEmbeddedValuesDefinition<DM, P, CX> by definition,
    IsPropertyDefinitionWrapper<Values<DM, P>, Values<DM, P>, CX, Any> {
    override val graphType = PropRefGraphType.PropRef

    override fun ref(parentRef: AnyPropertyReference?) =
        EmbeddedValuesPropertyRef(
            this,
            parentRef?.let {
                it as CanHaveComplexChildReference<*, *, *, *>
            }
        )

    /** Get a top level reference on a model with [propertyDefinitionGetter] */
    infix fun <T : Any, W : IsPropertyDefinitionWrapper<T, *, *, *>> ref(
        propertyDefinitionGetter: P.() -> W
    ): (AnyOutPropertyReference?) -> IsPropertyReference<T, W, IsValues<P>> =
        { this.definition.dataModel.ref(this.ref(it), propertyDefinitionGetter) }

    /** Get a top level reference on a model with [propertyDefinitionGetter]. Used for contextual embed values property definitions. */
    fun <T : Any, W : IsPropertyDefinitionWrapper<T, *, *, *>, DM: IsDataModel<P2>, P2: PropertyDefinitions> ref(
        dataModel: DM,
        propertyDefinitionGetter: P2.() -> W
    ): (AnyOutPropertyReference?) -> IsPropertyReference<T, W, IsValues<P2>> =
        { dataModel.ref(this.ref(it), propertyDefinitionGetter) }

    /** For quick notation to fetch property references with [referenceGetter] within embedded object */
    operator fun <T : Any, W : IsPropertyDefinition<T>, R : IsPropertyReference<T, W, *>> invoke(
        referenceGetter: P.() -> (AnyOutPropertyReference?) -> R
    ): (AnyOutPropertyReference?) -> R =
        { this.definition.dataModel(this.ref(it), referenceGetter) }
}
