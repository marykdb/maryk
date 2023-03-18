package maryk.core.properties.definitions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.properties.ContextualModel
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextTransformerDefinition
import maryk.core.properties.definitions.contextual.ContextValueTransformDefinition
import maryk.core.properties.definitions.contextual.ContextualSubDefinition
import maryk.core.properties.definitions.contextual.MultiTypeDefinitionContext
import maryk.core.properties.definitions.wrapper.DefinitionWrapperDelegateLoader
import maryk.core.properties.definitions.wrapper.MultiTypeDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ObjectDefinitionWrapperDelegateLoader
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.enum.MultiTypeEnumDefinition
import maryk.core.properties.types.TypedValue
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.values.ObjectValues
import maryk.lib.exceptions.ParseException

/**
 * Definition for objects which can be of multiple defined types.
 */
data class MultiTypeDefinition<E : MultiTypeEnum<out T>, T: Any>(
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val typeEnum: MultiTypeEnumDefinition<E>,
    override val typeIsFinal: Boolean = true,
    override val default: TypedValue<E, T>? = null
) : IsMultiTypeDefinition<E, T, ContainsDefinitionsContext>,
    IsUsableInMultiType<TypedValue<E, T>, ContainsDefinitionsContext> {
    override val propertyDefinitionType = PropertyDefinitionType.MultiType
    override val wireType = LENGTH_DELIMITED

    init {
        if (this.final) {
            require(this.typeIsFinal) { "typeIsFinal should be true if multi type definition is final" }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun definition(index: UInt) = typeEnum.resolve(index)?.definition as? IsSubDefinition<out Any, ContainsDefinitionsContext>?
    @Suppress("UNCHECKED_CAST")
    override fun definition(type: E) = type.definition as IsSubDefinition<T, ContainsDefinitionsContext>?

    override fun keepAsValues() = false

    override fun compatibleWith(
        definition: IsPropertyDefinition<*>,
        addIncompatibilityReason: ((String) -> Unit)?
    ): Boolean {
        var compatible = super<IsMultiTypeDefinition>.compatibleWith(definition, addIncompatibilityReason)

        if (definition is MultiTypeDefinition<*, *>) {
            compatible = typeEnum.compatibleWith(definition.typeEnum, addIncompatibilityReason) && compatible
        }

        return compatible
    }

    @Suppress("unused")
    object Model : ContextualModel<MultiTypeDefinition<*, *>, Model, ContainsDefinitionsContext, MultiTypeDefinitionContext>(
        contextTransformer = { MultiTypeDefinitionContext(it) },
    ) {
        val required by boolean(1u, MultiTypeDefinition<*, *>::required, default = true)
        val final by boolean(2u, MultiTypeDefinition<*, *>::final, default = false)
        val typeEnum by contextual(
            index = 3u,
            getter = MultiTypeDefinition<*, *>::typeEnum,
            definition = ContextValueTransformDefinition(
                definition = ContextTransformerDefinition(
                    definition = EmbeddedObjectDefinition(
                        dataModel = { MultiTypeEnumDefinition.Model }
                    ),
                    contextTransformer = {
                        it?.definitionsContext
                    }
                ),
                valueTransformer = { context: MultiTypeDefinitionContext?, value ->
                    if (value.optionalCases == null) {
                        context?.let { c ->
                            c.definitionsContext?.let {
                                it.typeEnums[value.name]
                                    ?: throw ParseException("TypeEnum ${value.name} is not Defined")
                            }
                        } ?: throw ContextNotFoundException()
                    } else {
                        value
                    }
                }
            ),
            capturer = { context, value ->
                context.multiTypeEnumDefinition = value
            }
        )

        val typeIsFinal by boolean(4u, MultiTypeDefinition<*, *>::typeIsFinal, default = true)

        val default by contextual(
            index = 5u,
            getter = MultiTypeDefinition<*, *>::default,
            definition = ContextualSubDefinition(
                required = false,
                contextTransformer = { context: MultiTypeDefinitionContext? ->
                    context?.definitionsContext
                },
                contextualResolver = { context: MultiTypeDefinitionContext? ->
                    context?.multiTypeDefinition ?: throw ContextNotFoundException()
                }
            )
        )

        override fun invoke(values: ObjectValues<MultiTypeDefinition<*, *>, Model>): MultiTypeDefinition<*, *> =
            MultiTypeDefinition<MultiTypeEnum<Any>, Any>(
                required = values(1u),
                final = values(2u),
                typeEnum = values(3u),
                typeIsFinal = values(4u),
                default = values(5u)
            )
    }
}

fun <E : MultiTypeEnum<out T>, T: Any> IsValuesPropertyDefinitions.multiType(
    index: UInt,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    typeEnum: MultiTypeEnumDefinition<E>,
    typeIsFinal: Boolean = true,
    default: TypedValue<E, T>? = null,
    alternativeNames: Set<String>? = null
) = DefinitionWrapperDelegateLoader(this) { propName ->
    MultiTypeDefinitionWrapper<E, T, TypedValue<E, T>, ContainsDefinitionsContext, Any>(
        index,
        name ?: propName,
        MultiTypeDefinition(required, final, typeEnum, typeIsFinal, default),
        alternativeNames
    )
}

fun <E : MultiTypeEnum<out T>, T: Any, TO: Any, DO: Any> ObjectPropertyDefinitions<DO>.multiType(
    index: UInt,
    getter: (DO) -> TO?,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    typeEnum: MultiTypeEnumDefinition<E>,
    typeIsFinal: Boolean = true,
    default: TypedValue<E, T>? = null,
    alternativeNames: Set<String>? = null
): ObjectDefinitionWrapperDelegateLoader<MultiTypeDefinitionWrapper<E, T, TO, ContainsDefinitionsContext, DO>, DO, ContainsDefinitionsContext> =
    multiType(index, getter, name, required, final, typeEnum, typeIsFinal, default, alternativeNames, toSerializable = null)

fun <E : MultiTypeEnum<out T>, T: Any, TO: Any, DO: Any, CX: ContainsDefinitionsContext> ObjectPropertyDefinitions<DO>.multiType(
    index: UInt,
    getter: (DO) -> TO?,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    typeEnum: MultiTypeEnumDefinition<E>,
    typeIsFinal: Boolean = true,
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
        MultiTypeDefinition(required, final, typeEnum, typeIsFinal, default),
        alternativeNames,
        getter = getter,
        capturer = capturer,
        toSerializable = toSerializable,
        fromSerializable = fromSerializable,
        shouldSerialize = shouldSerialize
    )
}
