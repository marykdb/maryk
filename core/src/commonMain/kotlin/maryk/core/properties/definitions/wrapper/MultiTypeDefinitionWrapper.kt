package maryk.core.properties.definitions.wrapper

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.graph.PropRefGraphType.PropRef
import maryk.core.properties.invoke
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.MultiTypePropertyReference
import maryk.core.properties.references.TypeReference
import maryk.core.properties.references.TypedValueReference
import maryk.core.properties.types.TypedValue
import maryk.core.values.Values
import kotlin.reflect.KProperty

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
    override val capturer: (Unit.(CX, TypedValue<E, T>) -> Unit)? = null,
    override val toSerializable: (Unit.(TO?, CX?) -> TypedValue<E, T>?)? = null,
    override val fromSerializable: (Unit.(TypedValue<E, T>?) -> TO?)? = null,
    override val shouldSerialize: (Unit.(Any) -> Boolean)? = null
) :
    AbstractDefinitionWrapper(index, name),
    IsMultiTypeDefinition<E, T, CX> by definition,
    IsDefinitionWrapper<TypedValue<E, T>, TO, CX, DO> {
    override val graphType = PropRef

    val typeRefCache: AtomicRef<Array<IsPropertyReference<*, *, *>>?> =
        atomic(null)
    val typeValueRefCache: AtomicRef<Array<IsPropertyReference<*, *, *>>?> =
        atomic(null)

    override fun ref(parentRef: AnyPropertyReference?) = cacheRef(parentRef) {
        MultiTypePropertyReference(this, parentRef)
    }

    override fun typeRef(parentReference: CanHaveComplexChildReference<TypedValue<E, T>, IsMultiTypeDefinition<E, T, *>, *, *>?) = this.ref(parentReference).let { parentRef ->
        cacheRef(parentRef, typeRefCache) {
            this.definition.typeRef(parentRef)
        }
    }

    private fun typedValueReference(type: E, parentReference: AnyPropertyReference?) = this.ref(parentReference).let { ref ->
        cacheRef(ref, typeValueRefCache, { (it.parentReference as MultiTypePropertyReference<*, *, *, *, *>).parentReference === parentReference && it.type == type}) {
            super.typedValueRef(type, ref)
        }
    }

    /** For quick notation to get a [type] reference */
    infix fun refAtType(type: E): (AnyOutPropertyReference?) -> TypedValueReference<E, T, CX> =
        { this.typedValueReference(type, it) }

    /** For quick notation to get an any type reference */
    fun refToType(): (AnyOutPropertyReference?) -> TypeReference<E, T, CX> = {
        @Suppress("UNCHECKED_CAST")
        this.typeRef(it as CanHaveComplexChildReference<TypedValue<E, T>, IsMultiTypeDefinition<E, T, *>, *, *>?)
    }

    /** Specific extension to support fetching deeper references with [type] */
    @Suppress("UNCHECKED_CAST")
    fun <DM : IsValuesPropertyDefinitions, T : Any, R : IsPropertyReference<T, IsDefinitionWrapper<T, *, *, *>, *>> withType(
        type: TypeEnum<Values<DM>>,
        referenceGetter: DM.() ->
            (AnyOutPropertyReference?) -> R
    ): (AnyOutPropertyReference?) -> R  =
        {
            val typeRef = this.typedValueReference(type as E, it)
            (this.definition(type) as EmbeddedValuesDefinition<DM>).dataModel(
                typeRef,
                referenceGetter
            )
        }

    // For delegation in definition
    operator fun getValue(thisRef: Any, property: KProperty<*>) = this
}
