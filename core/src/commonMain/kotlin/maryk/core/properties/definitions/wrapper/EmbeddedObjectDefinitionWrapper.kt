package maryk.core.properties.definitions.wrapper

import maryk.core.models.IsSimpleBaseObjectDataModel
import maryk.core.models.invoke
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsEmbeddedObjectDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.graph.PropRefGraphType.PropRef
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.EmbeddedObjectPropertyRef
import maryk.core.properties.references.IsPropertyReference
import maryk.core.values.AbstractValues
import maryk.core.values.ObjectValues
import maryk.core.values.ValueItem
import kotlin.reflect.KProperty

/**
 * Contains a Embedded Object property [definition] containing DataObjects of [EODO] in a BaseDataModel of [DM]
 * It contains an [index] and [name] to which it is referred inside ObjectDataModel and a [getter]
 * function to retrieve value on dataObject of [DO]
 * It has an input context of [CXI] and the functions take context of [CX] so contexts can be transformed
 * to be relevant to the Embedded Object
 */
data class EmbeddedObjectDefinitionWrapper<
    EODO : Any,
    TO : Any,
    DM : IsSimpleBaseObjectDataModel<EODO, CXI, CX>,
    CXI : IsPropertyContext, CX : IsPropertyContext, in DO : Any
> internal constructor(
    override val index: UInt,
    override val name: String,
    override val definition: IsEmbeddedObjectDefinition<EODO, DM, CXI, CX>,
    override val alternativeNames: Set<String>? = null,
    override val getter: (DO) -> TO? = { null },
    override val capturer: (Unit.(CXI, EODO) -> Unit)? = null,
    override val toSerializable: (Unit.(TO?, CXI?) -> EODO?)? = null,
    override val fromSerializable: (Unit.(EODO?) -> TO?)? = null,
    override val shouldSerialize: (Unit.(Any) -> Boolean)? = null
) :
    AbstractDefinitionWrapper(index, name),
    IsEmbeddedObjectDefinition<EODO, DM, CXI, CX> by definition,
    IsDefinitionWrapper<EODO, TO, CXI, DO> {
    override val graphType = PropRef

    override fun ref(parentRef: AnyPropertyReference?) = cacheRef(parentRef) {
        EmbeddedObjectPropertyRef(
            this,
            parentRef?.let {
                it as CanHaveComplexChildReference<*, *, *, *>
            }
        )
    }

    /** Create an index [value] pair for maps */
    infix fun with(value: ObjectValues<EODO, DM>?) = value?.let {
        ValueItem(this.index, value)
    }

    /** Get a top level reference on a model with [propertyDefinitionGetter] */
    infix fun <T : Any, W : IsDefinitionWrapper<T, *, *, AbstractValues<*, *>>> ref(
        propertyDefinitionGetter: DM.() -> W
    ): (AnyOutPropertyReference?) -> IsPropertyReference<T, W, *> =
        {
            @Suppress("UNCHECKED_CAST")
            propertyDefinitionGetter(
                this.definition.dataModel
            ).ref(this.ref(it)) as IsPropertyReference<T, W, *>
        }

    /** For quick notation to fetch property references with [referenceGetter] within embedded object */
    operator fun <T : Any, W : IsPropertyDefinition<T>, R : IsPropertyReference<T, W, *>> invoke(
        referenceGetter: DM.() -> (AnyOutPropertyReference?) -> R
    ): (AnyOutPropertyReference?) -> R =
        { this.definition.dataModel(this.ref(it), referenceGetter) }

    // For delegation in definition
    operator fun getValue(thisRef: Any, property: KProperty<*>) = this
}
