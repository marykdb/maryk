package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.wrapper.MultiTypeDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ObjectDefinitionWrapperDelegateLoader
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.types.TypedValue
import maryk.core.protobuf.WireType.LENGTH_DELIMITED

/**
 * Definition for objects which can be of multiple defined types.
 * Only for internal use to define contextual transformers/resolvers. Cannot be transported.
 * The type mapping is defined in the given [definitionMap] mapped by enum [E].
 * Receives context of [CX]
 */
data class InternalMultiTypeDefinition<E : TypeEnum<T>, T: Any, in CX : IsPropertyContext> internal constructor(
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val typeEnum: IndexedEnumDefinition<E>,
    override val typeIsFinal: Boolean = true,
    val definitionMap: Map<E, IsSubDefinition<out Any, CX>>,
    override val default: TypedValue<E, T>? = null,
    internal val keepAsValues: Boolean = false
) : IsMultiTypeDefinition<E, T, CX>, IsUsableInMultiType<TypedValue<E, T>, CX> {
    override val propertyDefinitionType = PropertyDefinitionType.MultiType
    override val wireType = LENGTH_DELIMITED

    private val definitionMapByIndex = definitionMap.map { Pair(it.key.index, it.value) }.toMap()

    init {
        if (this.final) {
            require(this.typeIsFinal) { "typeIsFinal should be true if multi type definition is final" }
        }
    }

    constructor(
        required: Boolean = true,
        final: Boolean = false,
        typeEnum: IndexedEnumDefinition<E>,
        typeIsFinal: Boolean = true,
        definitionMap: Map<E, IsUsableInMultiType<out Any, CX>>,
        default: TypedValue<E, T>? = null
    ) : this(
        required = required,
        final = final,
        typeEnum = typeEnum,
        typeIsFinal = typeIsFinal,
        definitionMap = definitionMap as Map<E, IsSubDefinition<out Any, CX>>,
        default = default
    )

    override fun definition(index: UInt) = definitionMapByIndex[index]

    override fun keepAsValues() = this.keepAsValues

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InternalMultiTypeDefinition<*, *, *>) return false

        if (required != other.required) return false
        if (final != other.final) return false
        if (typeIsFinal != other.typeIsFinal) return false
        if (definitionMap != other.definitionMap) {
            if (definitionMap.size != other.definitionMap.size) return false
            definitionMap.entries.zip(other.definitionMap.entries).map {
                if (it.first.key.index != it.second.key.index
                    || it.first.key.name != it.second.key.name
                    || it.first.value != it.second.value
                ) {
                    return false
                }
            }
        }

        return true
    }

    override fun hashCode(): Int {
        var result = required.hashCode()
        result = 31 * result + final.hashCode()
        result = 31 * result + definitionMap.hashCode()
        result = 31 * result + typeIsFinal.hashCode()
        return result
    }
}

internal fun <E : TypeEnum<T>, T: Any, TO: Any, CX: IsPropertyContext, DO: Any> ObjectPropertyDefinitions<DO>.internalMultiType(
    index: UInt,
    getter: (DO) -> TO?,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    typeEnum: IndexedEnumDefinition<E>,
    typeIsFinal: Boolean = true,
    definitionMap: Map<E, IsSubDefinition<out Any, CX>>,
    default: TypedValue<E, T>? = null,
    alternativeNames: Set<String>? = null
): ObjectDefinitionWrapperDelegateLoader<MultiTypeDefinitionWrapper<E, T, TO, CX, DO>, DO, CX> =
    internalMultiType(index, getter, name, required, final, typeEnum, typeIsFinal, definitionMap, default, alternativeNames, toSerializable = null)

internal fun <E : TypeEnum<T>, T: Any, TO: Any, DO: Any, CX: IsPropertyContext> ObjectPropertyDefinitions<DO>.internalMultiType(
    index: UInt,
    getter: (DO) -> TO?,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    typeEnum: IndexedEnumDefinition<E>,
    typeIsFinal: Boolean = true,
    definitionMap: Map<E, IsSubDefinition<out Any, CX>>,
    default: TypedValue<E, T>? = null,
    alternativeNames: Set<String>? = null,
    toSerializable: (Unit.(TO?, CX?) -> TypedValue<E, T>?)? = null,
    fromSerializable: (Unit.(TypedValue<E, T>?) -> TO?)? = null,
    shouldSerialize: (Unit.(Any) -> Boolean)? = null,
    capturer: (Unit.(CX, TypedValue<E, T>) -> Unit)? = null
) = ObjectDefinitionWrapperDelegateLoader(this) { propName ->
    MultiTypeDefinitionWrapper(
        index,
        name ?: propName,
        InternalMultiTypeDefinition(required, final, typeEnum, typeIsFinal, definitionMap, default),
        alternativeNames,
        getter = getter,
        capturer = capturer,
        toSerializable = toSerializable,
        fromSerializable = fromSerializable,
        shouldSerialize = shouldSerialize
    )
}
