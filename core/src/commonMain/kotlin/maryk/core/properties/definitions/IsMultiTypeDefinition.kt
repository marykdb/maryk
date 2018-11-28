package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.TypeReference
import maryk.core.properties.types.TypedValue

/** Defines a multi type definition */
interface IsMultiTypeDefinition<E: IndexedEnum<E>, in CX: IsPropertyContext> :
    IsValueDefinition<TypedValue<E, Any>, CX>,
    IsSerializablePropertyDefinition<TypedValue<E, Any>, CX>,
    IsTransportablePropertyDefinitionType<TypedValue<E, Any>>,
    HasDefaultValueDefinition<TypedValue<E, Any>>
{
    val definitionMap: Map<E, IsSubDefinition<out Any, CX>>

    /**
     * Creates a reference referring to [type] of multi type below [parentReference]
     * so reference can be strongly typed
     */
    fun getTypeRef(type: E, parentReference: CanHaveComplexChildReference<*, *, *, *>?) =
        TypeReference(type, this, parentReference)

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
