package maryk.core.properties.definitions.wrapper

import maryk.core.models.AbstractObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
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

/**
 * Contains a Embedded Object property [definition] containing DataObjects of [EODO] and Properties described by [P]
 * in a ObjectDataModel of [DM]
 * It contains an [index] and [name] to which it is referred inside ObjectDataModel and a [getter]
 * function to retrieve value on dataObject of [DO]
 * It has an input context of [CXI] and the functions take context of [CX] so contexts can be transformed
 * to be relevant to the Embedded Object
 */
data class EmbeddedObjectDefinitionWrapper<
    EODO : Any,
    TO : Any,
    P : ObjectPropertyDefinitions<EODO>,
    out DM : AbstractObjectDataModel<EODO, P, CXI, CX>,
    CXI : IsPropertyContext, CX : IsPropertyContext, in DO : Any
> internal constructor(
    override val index: UInt,
    override val name: String,
    override val definition: IsEmbeddedObjectDefinition<EODO, P, DM, CXI, CX>,
    override val getter: (DO) -> TO?,
    override val capturer: ((CXI, EODO) -> Unit)? = null,
    override val toSerializable: ((TO?, CXI?) -> EODO?)? = null,
    override val fromSerializable: ((EODO?) -> TO?)? = null,
    override val shouldSerialize: ((Any) -> Boolean)? = null
) :
    AbstractDefinitionWrapper(index, name),
    IsEmbeddedObjectDefinition<EODO, P, DM, CXI, CX> by definition,
    IsDefinitionWrapper<EODO, TO, CXI, DO> {
    override val graphType = PropRef

    override fun ref(parentRef: AnyPropertyReference?) =
        EmbeddedObjectPropertyRef(
            this,
            parentRef?.let {
                it as CanHaveComplexChildReference<*, *, *, *>
            }
        )

    /** Create an index [value] pair for maps */
    infix fun with(value: ObjectValues<EODO, P>?) = value?.let {
        ValueItem(this.index, value)
    }

    /** Get a top level reference on a model with [propertyDefinitionGetter] */
    infix fun <T : Any, W : IsDefinitionWrapper<T, *, *, AbstractValues<*, *, *>>> ref(
        propertyDefinitionGetter: P.() -> W
    ): (AnyOutPropertyReference?) -> IsPropertyReference<T, W, *> =
        {
            @Suppress("UNCHECKED_CAST")
            propertyDefinitionGetter(
                this.definition.dataModel.properties
            ).ref(this.ref(it)) as IsPropertyReference<T, W, *>
        }

    /** For quick notation to fetch property references with [referenceGetter] within embedded object */
    operator fun <T : Any, W : IsPropertyDefinition<T>, R : IsPropertyReference<T, W, *>> invoke(
        referenceGetter: P.() -> (AnyOutPropertyReference?) -> R
    ): (AnyOutPropertyReference?) -> R =
        { this.definition.dataModel(this.ref(it), referenceGetter) }
}
