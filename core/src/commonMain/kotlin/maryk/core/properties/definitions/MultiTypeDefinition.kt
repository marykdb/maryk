package maryk.core.properties.definitions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.ContextualDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.definitions.contextual.MultiTypeDefinitionContext
import maryk.core.properties.definitions.descriptors.addDescriptorPropertyWrapperWrapper
import maryk.core.properties.definitions.descriptors.convertMultiTypeDescriptors
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.types.TypedValue
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.values.SimpleObjectValues

/**
 * Definition for objects which can be of multiple defined types.
 * The type mapping is defined in the given [definitionMap] mapped by enum [E].
 * Receives context of [CX]
 */
data class MultiTypeDefinition<E : TypeEnum<T>, T: Any, in CX : IsPropertyContext> internal constructor(
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val typeEnum: IndexedEnumDefinition<E>,
    override val typeIsFinal: Boolean = true,
    override val definitionMap: Map<E, IsSubDefinition<out Any, CX>>,
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
        if (other !is MultiTypeDefinition<*, *, *>) return false

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

    object Model :
        ContextualDataModel<MultiTypeDefinition<*, *, *>, ObjectPropertyDefinitions<MultiTypeDefinition<*, *, *>>, ContainsDefinitionsContext, MultiTypeDefinitionContext>(
            contextTransformer = { MultiTypeDefinitionContext(it) },
            properties = object : ObjectPropertyDefinitions<MultiTypeDefinition<*, *, *>>() {
                init {
                    IsPropertyDefinition.addRequired(this, MultiTypeDefinition<*, *, *>::required)
                    IsPropertyDefinition.addFinal(this, MultiTypeDefinition<*, *, *>::final)

                    add(3u, "typeEnum",
                        StringDefinition(),
                        getter = MultiTypeDefinition<*, *, *>::typeEnum,
                        capturer = { context: MultiTypeDefinitionContext, value: String ->
                            context.typeEnumName = value
                        },
                        toSerializable = { value: IndexedEnumDefinition<out TypeEnum<Any>>?, _ ->
                            value?.name
                        },
                        fromSerializable = { null }
                    )

                    add(4u, "typeIsFinal", BooleanDefinition(default = true), MultiTypeDefinition<*, *, *>::typeIsFinal)

                    this.addDescriptorPropertyWrapperWrapper(5u, "definitionMap")

                    add(6u, "default",
                        ContextualValueDefinition(
                            required = false,
                            contextTransformer = { context: MultiTypeDefinitionContext? ->
                                context?.definitionsContext
                            },
                            contextualResolver = { context: MultiTypeDefinitionContext? ->
                                context?.multiTypeDefinition ?: throw ContextNotFoundException()
                            }
                        ),
                        MultiTypeDefinition<*, *, *>::default
                    )
                }
            }
        ) {
        override fun invoke(values: SimpleObjectValues<MultiTypeDefinition<*, *, *>>): MultiTypeDefinition<TypeEnum<Any>, Any, ContainsDefinitionsContext> {
            val definitionMap = convertMultiTypeDescriptors(
                values(5u)
            )

            val typeOptions = definitionMap.keys.toTypedArray()

            val typeEnum = IndexedEnumDefinition(
                name = values(3u),
                values = { typeOptions }
            )

            return MultiTypeDefinition(
                required = values(1u),
                final = values(2u),
                typeEnum = typeEnum,
                typeIsFinal = values(4u),
                definitionMap = definitionMap,
                default = values(6u)
            )
        }
    }
}
