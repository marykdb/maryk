package maryk.core.properties.definitions.wrapper

import maryk.core.objects.AbstractDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSubModelDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.SubModelDefinition
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.SubModelPropertyRef

/**
 * Contains a Sub Model property [definition] containing DataObjects of [SDO] and Properties described by [P]
 * in a DataModel of [DM]
 * It contains an [index] and [name] to which it is referred inside DataModel and a [getter]
 * function to retrieve value on dataObject of [DO]
 * It has an input context of [CXI] and the functions take context of [CX] so contexts can be transformed
 * to be relevant to the Sub DataModel
 */
data class SubModelPropertyDefinitionWrapper<SDO: Any, out P: PropertyDefinitions<SDO>, out DM: AbstractDataModel<SDO, P, CXI, CX>, CXI: IsPropertyContext, CX: IsPropertyContext, in DO: Any>(
    override val index: Int,
    override val name: String,
    override val definition: SubModelDefinition<SDO, P, DM, CXI, CX>,
    override val getter: (DO) -> SDO?
) :
    IsSubModelDefinition<SDO, CXI> by definition,
    IsPropertyDefinitionWrapper<SDO, CXI, DO>
{
    override fun getRef(parentRef: IsPropertyReference<*, *>?) =
        SubModelPropertyRef(
            this,
            parentRef?.let {
                it as CanHaveComplexChildReference<*, *, *>
            }
        )

    /** Get a top level reference on a model with [propertyDefinitionGetter] */
    infix fun <T: Any, W: IsPropertyDefinitionWrapper<T, *, *>> ref(
        propertyDefinitionGetter: P.()-> W
    ): (IsPropertyReference<out Any, IsPropertyDefinition<*>>?) -> IsPropertyReference<T, W> =
        { this.definition.dataModel.ref(this.getRef(it), propertyDefinitionGetter) }

    /** For quick notation to fetch property references with [referenceGetter] below sub models */
    operator fun <T: Any, W: IsPropertyDefinition<T>> invoke(
        referenceGetter: P.() ->
            (IsPropertyReference<out Any, IsPropertyDefinition<*>>?) ->
        IsPropertyReference<T, W>
    ): (IsPropertyReference<out Any, IsPropertyDefinition<*>>?) -> IsPropertyReference<T, W> {
        return { this.definition.dataModel(this.getRef(it), referenceGetter) }
    }
}