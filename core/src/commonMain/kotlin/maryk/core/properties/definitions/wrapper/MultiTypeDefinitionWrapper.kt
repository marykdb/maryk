package maryk.core.properties.definitions.wrapper

import maryk.core.models.IsValuesDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.graph.PropRefGraphType
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.MultiTypePropertyReference
import maryk.core.properties.references.TypeReference
import maryk.core.properties.types.TypedValue

/**
 * Contains a Multi Type property [definition] containing type [E]
 * It contains an [index] and [name] to which it is referred inside DataModel and a [getter]
 * function to retrieve value on dataObject of [DO] in context [CX]
 */
data class MultiTypeDefinitionWrapper<E: IndexedEnum<E>, TO: Any, CX: IsPropertyContext, DO: Any> internal constructor(
    override val index: Int,
    override val name: String,
    override val definition: IsMultiTypeDefinition<E, CX>,
    override val getter: (DO) -> TO? = { null },
    override val capturer: ((CX, TypedValue<E, Any>) -> Unit)? = null,
    override val toSerializable: ((TO?, CX?) -> TypedValue<E, Any>?)? = null,
    override val fromSerializable: ((TypedValue<E, Any>?) -> TO?)? = null,
    override val shouldSerialize: ((Any) -> Boolean)? = null
) :
    AbstractPropertyDefinitionWrapper(index, name),
    IsMultiTypeDefinition<E, CX> by definition,
    IsPropertyDefinitionWrapper<TypedValue<E, Any>, TO, CX, DO>
{
    override val graphType = PropRefGraphType.PropRef

    override fun getRef(parentRef: AnyPropertyReference?) =
        MultiTypePropertyReference(this, parentRef)

    /** For quick notation to get a [type] reference */
    infix fun ofType(type: E): (IsPropertyReference<out Any, IsPropertyDefinition<*>, *>?) -> TypeReference<E, CX> {
        return { this.getTypeRef(type, this.getRef(it)) }
    }

    /** Specific extension to support fetching deeper references with [type] */
    @Suppress("UNCHECKED_CAST")
    fun <P: PropertyDefinitions, T: Any, R: IsPropertyReference<T, IsPropertyDefinitionWrapper<T, *, *, *>, *>> withType(
        type: E,
        @Suppress("UNUSED_PARAMETER") properties: P, // So it is not needed to pass in types
        referenceGetter: P.() ->
            (IsPropertyReference<out Any, IsPropertyDefinition<*>, *>?) -> R
    ): (IsPropertyReference<out Any, IsPropertyDefinition<*>, *>?) -> R =
        {
            val typeRef = this.getTypeRef(type, this.getRef(it))
            (this.definitionMap[type] as EmbeddedValuesDefinition<IsValuesDataModel<P>, P>).dataModel(typeRef, referenceGetter)
        }
}
