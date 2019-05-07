package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.TypeReference
import maryk.core.properties.references.TypedValueReference
import maryk.core.properties.types.TypedValue

/** Defines a multi type definition */
interface IsMultiTypeDefinition<E : TypeEnum<Any>, in CX : IsPropertyContext> :
    IsValueDefinition<TypedValue<E, Any>, CX>,
    IsSerializablePropertyDefinition<TypedValue<E, Any>, CX>,
    IsTransportablePropertyDefinitionType<TypedValue<E, Any>>,
    HasDefaultValueDefinition<TypedValue<E, Any>>,
    IsUsableInMapValue<TypedValue<E, Any>, CX> {
    val typeIsFinal: Boolean
    val typeEnum: IndexedEnumDefinition<E>
    val definitionMap: Map<E, IsSubDefinition<out Any, CX>>

    /** Get definition by [index] */
    fun definition(index: UInt): IsSubDefinition<out Any, CX>?

    /**
     * Creates a reference referring to a value of [type] of multi type below [parentReference]
     * so reference can be strongly typed
     */
    fun typedValueRef(type: E, parentReference: CanHaveComplexChildReference<*, *, *, *>?) =
        TypedValueReference(type, this, parentReference)

    /** Creates a reference referring to any type of multi type below [parentReference] */
    fun typeRef(parentReference: CanHaveComplexChildReference<TypedValue<E, *>, IsMultiTypeDefinition<E, *>, *, *>? = null) =
        TypeReference(
            this,
            parentReference
        )

    /** Resolve a reference from [reader] found on a [parentReference] */
    fun resolveReference(
        reader: () -> Byte,
        parentReference: CanHaveComplexChildReference<*, *, *, *>? = null
    ): IsPropertyReference<Any, *, *>

    /** Resolve a stored reference from [reader] found on a [parentReference] */
    fun resolveReferenceFromStorage(
        reader: () -> Byte,
        parentReference: CanHaveComplexChildReference<*, *, *, *>? = null
    ): IsPropertyReference<Any, *, *>

    /** Resolve a reference from [name] found on a [parentReference] */
    fun resolveReferenceByName(
        name: String,
        parentReference: CanHaveComplexChildReference<*, *, *, *>? = null
    ): IsPropertyReference<Any, *, *>
}
