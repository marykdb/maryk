package maryk.core.properties.definitions.wrapper

import maryk.core.models.IsDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.properties.definitions.IsEmbeddedValuesDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.graph.PropRefGraphType.PropRef
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.EmbeddedValuesPropertyRef
import maryk.core.properties.references.IsPropertyReference
import maryk.core.values.IsValues
import maryk.core.values.Values
import kotlin.reflect.KProperty

/**
 * Contains an Embedded Values property [definition] containing Values and Properties described by [DM]
 * in a DataModel of [DM]
 * It contains an [index] and [name] to which it is referred inside DataModel
 * It has an input context of [CX]
 */
data class EmbeddedValuesDefinitionWrapper<
    DM : IsValuesPropertyDefinitions,
    CX : IsPropertyContext
> internal constructor(
    override val index: UInt,
    override val name: String,
    override val definition: IsEmbeddedValuesDefinition<DM, CX>,
    override val alternativeNames: Set<String>? = null,
    override val getter: (Any) -> Values<DM>? = { null },
    override val capturer: (Unit.(CX, Values<DM>) -> Unit)? = null,
    override val toSerializable: (Unit.(Values<DM>?, CX?) -> Values<DM>?)? = null,
    override val fromSerializable: (Unit.(Values<DM>?) -> Values<DM>?)? = null,
    override val shouldSerialize: (Unit.(Any) -> Boolean)? = null
) :
    AbstractDefinitionWrapper(index, name),
    IsEmbeddedValuesDefinition<DM, CX> by definition,
    IsDefinitionWrapper<Values<DM>, Values<DM>, CX, Any> {
    override val graphType = PropRef

    override fun ref(parentRef: AnyPropertyReference?) = cacheRef(parentRef, refCache) {
        EmbeddedValuesPropertyRef(
            this,
            parentRef?.let {
                it as CanHaveComplexChildReference<*, *, *, *>
            }
        )
    }

    /** Get a top-level reference on a model with [propertyDefinitionGetter]. Used for contextual embed values property definitions. */
    fun <T : Any, W : IsDefinitionWrapper<T, *, *, *>, DM: IsDataModel<P2>, P2: IsValuesPropertyDefinitions> refWithDM(
        dataModel: DM,
        propertyDefinitionGetter: P2.() -> W
    ): (AnyOutPropertyReference?) -> IsPropertyReference<T, W, IsValues<P2>> =
        {
            @Suppress("UNCHECKED_CAST")
            propertyDefinitionGetter(dataModel.properties)
                .ref(this.ref(it)) as IsPropertyReference<T, W, IsValues<P2>>
        }

    /** For quick notation to fetch property references with [referenceGetter] within embedded object */
    operator fun <T : Any, W : IsPropertyDefinition<T>, R : IsPropertyReference<T, W, *>> invoke(
        referenceGetter: DM.() -> (AnyOutPropertyReference?) -> R
    ): (AnyOutPropertyReference?) -> R =
        { this.definition.dataModel(this.ref(it), referenceGetter) }

    // For delegation in definition
    operator fun getValue(thisRef: Any, property: KProperty<*>) = this
}
