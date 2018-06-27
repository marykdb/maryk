package maryk.core.properties.definitions.wrapper

import maryk.core.models.AbstractDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsEmbeddedObjectDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.graph.PropRefGraphType
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.EmbeddedObjectPropertyRef
import maryk.core.properties.references.IsPropertyReference

/**
 * Contains a Embedded Object property [definition] containing DataObjects of [EODO] and Properties described by [P]
 * in a DataModel of [DM]
 * It contains an [index] and [name] to which it is referred inside DataModel and a [getter]
 * function to retrieve value on dataObject of [DO]
 * It has an input context of [CXI] and the functions take context of [CX] so contexts can be transformed
 * to be relevant to the Embedded Object
 */
data class EmbeddedObjectPropertyDefinitionWrapper<
    EODO: Any,
    TO: Any,
    P: PropertyDefinitions<EODO>,
    out DM: AbstractDataModel<EODO, P, CXI, CX>,
    CXI: IsPropertyContext, CX: IsPropertyContext, in DO: Any
> internal constructor(
    override val index: Int,
    override val name: String,
    override val definition: IsEmbeddedObjectDefinition<EODO, P, DM, CXI, CX>,
    override val getter: (DO) -> TO?,
    override val capturer: ((CXI, EODO) -> Unit)? = null,
    override val toSerializable: ((TO?, CXI?) -> EODO?)? = null,
    override val fromSerializable: ((EODO?) -> TO?)? = null
) :
    IsEmbeddedObjectDefinition<EODO, P, DM, CXI, CX> by definition,
    IsPropertyDefinitionWrapper<EODO, TO, CXI, DO>
{
    override val graphType = PropRefGraphType.PropRef

    override fun getRef(parentRef: IsPropertyReference<*, *>?) =
        EmbeddedObjectPropertyRef(
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
        runner: P.(EmbeddedObjectPropertyDefinitionWrapper<EODO, TO, P, DM, CXI, CX, DO>) -> T
    ): T {
        return runner(this.definition.dataModel.properties, this)
    }
}
