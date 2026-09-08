@file:OptIn(ExperimentalTypeInference::class)

package maryk.core.properties.definitions.wrapper

import kotlin.experimental.ExperimentalTypeInference
import kotlin.jvm.JvmName
import kotlin.reflect.KProperty
import maryk.core.models.IsValuesDataModel
import maryk.core.models.invoke
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.IsChangeableValueDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.graph.IsPropRefGraphNode
import maryk.core.properties.graph.PropRefGraphType.PropRef
import maryk.core.properties.graph.TypePropRefGraph
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.MultiTypePropertyReference
import maryk.core.properties.references.SimpleTypedValueReference
import maryk.core.properties.references.TypeReference
import maryk.core.properties.references.TypedValueReference
import maryk.core.properties.types.TypedValue
import maryk.core.values.Values

/**
 * Contains a Multi Type property [definition] containing type [E]
 * It contains an [index] and [name] to which it is referred inside DataModel, and a [getter]
 * function to retrieve value on dataObject of [DO] in context [CX]
 */
data class MultiTypeDefinitionWrapper<E : TypeEnum<T>, T: Any, TO : Any, in CX : IsPropertyContext, DO : Any> internal constructor(
    override val index: UInt,
    override val name: String,
    override val definition: IsMultiTypeDefinition<E, T, CX>,
    override val alternativeNames: Set<String>? = null,
    override val getter: (DO) -> TO? = { null },
    override val capturer: ((CX, TypedValue<E, T>) -> Unit)? = null,
    override val toSerializable: ((TO?, CX?) -> TypedValue<E, T>?)? = null,
    override val fromSerializable: ((TypedValue<E, T>?) -> TO?)? = null,
    override val shouldSerialize: ((Any) -> Boolean)? = null
) :
    AbstractDefinitionWrapper(index, name),
    IsReferenceCreator<MultiTypePropertyReference<E, T, TO, MultiTypeDefinitionWrapper<E, T, TO, CX, DO>, AnyPropertyReference>>,
    IsMultiTypeDefinition<E, T, CX> by definition,
    IsChangeableValueDefinition<TypedValue<E, T>, CX>,
    IsDefinitionWrapper<TypedValue<E, T>, TO, CX, DO> {
    override val graphType = PropRef

    override fun ref(parentRef: AnyPropertyReference?) = cacheRef(parentRef) {
        MultiTypePropertyReference(this, parentRef)
    }

    override fun typeRef(parentReference: AnyOutPropertyReference?) = this.ref(parentReference).let { parentRef ->
        cacheRef(parentRef) {
            this.definition.typeRef(parentRef)
        }
    }

    private fun typedValueReference(type: E, parentReference: AnyPropertyReference?) = this.ref(parentReference).let { ref ->
        cacheRef(ref, type to "*") {
            super.typedValueRef(type, ref)
        }
    }

    private fun simpleTypedValueReference(type: E, parentReference: AnyPropertyReference?): SimpleTypedValueReference<E, T, CX> = this.ref(parentReference).let { ref ->
        cacheRef(ref, type to ">") {
            super.simpleTypedValueRef(type, ref)
        }
    }

    /** Select a value reference for [type]. */
    infix fun atType(type: E): IsReferenceCreator<TypedValueReference<E, T, CX>> =
        IsReferenceCreator { this.typedValueReference(type, it) }

    /** Select a simple value reference for [type]. */
    infix fun simpleAtType(type: E): IsReferenceCreator<SimpleTypedValueReference<E, T, CX>> =
        IsReferenceCreator { this.simpleTypedValueReference(type, it as? CanHaveComplexChildReference<*, *, *, *>) }

    /** Select the discriminator reference. */
    val type: IsReferenceCreator<TypeReference<E, T, CX>>
        get() = IsReferenceCreator {
            @Suppress("UNCHECKED_CAST")
            this.typeRef(it as CanHaveComplexChildReference<TypedValue<E, T>, IsMultiTypeDefinition<E, T, *>, *, *>?)
        }

    /** @deprecated Use [atType]. */
    @Deprecated("Use atType(type)", ReplaceWith("atType(type)::ref"))
    infix fun refAtType(type: E): (AnyOutPropertyReference?) -> TypedValueReference<E, T, CX> =
        { this.atType(type).ref(it) }

    /** @deprecated Use [simpleAtType]. */
    @Deprecated("Use simpleAtType(type)", ReplaceWith("simpleAtType(type)::ref"))
    infix fun simpleRefAtType(type: E): (AnyOutPropertyReference?) -> SimpleTypedValueReference<E, T, CX> =
        { this.simpleAtType(type).ref(it) }

    /** @deprecated Use [type]. */
    @Deprecated("Use type", ReplaceWith("type::ref"))
    fun refToType(): (AnyOutPropertyReference?) -> TypeReference<E, T, CX> = { this.type.ref(it) }

    /** Select a child while preserving its concrete reference type. */
    @OverloadResolutionByLambdaReturnType
    @JvmName("withTypePropertySelection")
    fun <DM : IsValuesDataModel, T : Any, R : IsPropertyReference<T, *, *>> withType(
        type: TypeEnum<Values<DM>>,
        selector: DM.() -> IsReferenceCreator<R>
    ): (AnyOutPropertyReference?) -> R = withType(type, referenceGetter = { selector(this)::ref })

    /** Specific extension to support fetching deeper references with [type] */
    @Suppress("UNCHECKED_CAST")
    fun <DM : IsValuesDataModel, T : Any, W : IsPropertyDefinition<T>, R : IsPropertyReference<T, W, *>> withType(
        type: TypeEnum<Values<DM>>,
        referenceGetter: DM.() ->
            (AnyOutPropertyReference?) -> R
    ): (AnyOutPropertyReference?) -> R =
        {
            val typeRef = this.typedValueReference(type as E, it)
            (this.definition(type) as EmbeddedValuesDefinition<DM>).dataModel(
                typeRef,
                referenceGetter
            )
        }
    /** Build a graph for a specific [type] */
    @Suppress("UNCHECKED_CAST")
    fun <DMS : IsValuesDataModel, E2 : TypeEnum<Values<DMS>>> withTypeGraph(
        type: E2,
        graphGetter: DMS.() -> List<IsPropRefGraphNode<DMS>>
    ): TypePropRefGraph<IsValuesDataModel, DMS, E2> =
        TypePropRefGraph(
            parent = this as MultiTypeDefinitionWrapper<E2, Any, Any, IsPropertyContext, IsValuesDataModel>,
            type = type,
            properties = graphGetter((this.definition(type) as EmbeddedValuesDefinition<DMS>).dataModel).sortedBy { it.index }
        )


    // For delegation in definition
    @Suppress("unused")
    operator fun getValue(thisRef: Any, property: KProperty<*>) = this

    override fun validateWithRef(
        previousValue: TypedValue<E, T>?,
        newValue: TypedValue<E, T>?,
        refGetter: () -> IsPropertyReference<TypedValue<E, T>, IsPropertyDefinition<TypedValue<E, T>>, *>?
    ) {
        super<IsDefinitionWrapper>.validateWithRef(previousValue, newValue, refGetter)
        super<IsChangeableValueDefinition>.validateWithRef(previousValue, newValue, refGetter)
        super<IsMultiTypeDefinition>.validateWithRef(previousValue, newValue, refGetter)
    }
}
