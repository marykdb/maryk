package maryk.core.properties.definitions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.ContextualDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextTransformerDefinition
import maryk.core.properties.definitions.contextual.ContextValueTransformDefinition
import maryk.core.properties.definitions.contextual.ContextualSubDefinition
import maryk.core.properties.definitions.contextual.MultiTypeDefinitionContext
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.enum.MultiTypeEnumDefinition
import maryk.core.properties.types.TypedValue
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.values.SimpleObjectValues
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
) : IsMultiTypeDefinition<E, T, ContainsDefinitionsContext>, IsUsableInMultiType<TypedValue<E, T>, ContainsDefinitionsContext> {
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

    object Model :
        ContextualDataModel<MultiTypeDefinition<*, *>, ObjectPropertyDefinitions<MultiTypeDefinition<*, *>>, ContainsDefinitionsContext, MultiTypeDefinitionContext>(
            contextTransformer = { MultiTypeDefinitionContext(it) },
            properties = object : ObjectPropertyDefinitions<MultiTypeDefinition<*, *>>() {
                init {
                    IsPropertyDefinition.addRequired(this, MultiTypeDefinition<*, *>::required)
                    IsPropertyDefinition.addFinal(this, MultiTypeDefinition<*, *>::final)

                    @Suppress("UNCHECKED_CAST")
                    add(3u, "typeEnum",
                        ContextValueTransformDefinition(
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
                        getter = MultiTypeDefinition<*, *>::typeEnum as (MultiTypeDefinition<*, *>) -> MultiTypeEnumDefinition<MultiTypeEnum<*>>?,
                        capturer = { context, value ->
                            context.multiTypeEnumDefinition = value
                        }
                    )

                    add(4u, "typeIsFinal", BooleanDefinition(default = true), MultiTypeDefinition<*, *>::typeIsFinal)

                    add(5u, "default",
                        ContextualSubDefinition<MultiTypeDefinitionContext, ContainsDefinitionsContext, TypedValue<MultiTypeEnum<*>, *>, IsMultiTypeDefinition<MultiTypeEnum<*>, Any, ContainsDefinitionsContext>>(
                            required = false,
                            contextTransformer = { context: MultiTypeDefinitionContext? ->
                                context?.definitionsContext
                            },
                            contextualResolver = { context: MultiTypeDefinitionContext? ->
                                context?.multiTypeDefinition ?: throw ContextNotFoundException()
                            }
                        ) as ContextualSubDefinition<MultiTypeDefinitionContext, ContainsDefinitionsContext, TypedValue<MultiTypeEnum<*>, *>, *>,
                        MultiTypeDefinition<*, *>::default as (MultiTypeDefinition<*, *>) -> TypedValue<MultiTypeEnum<*>, *>?
                    )
                }
            }
        ) {
        override fun invoke(values: SimpleObjectValues<MultiTypeDefinition<*, *>>) =
            MultiTypeDefinition(
                required = values(1u),
                final = values(2u),
                typeEnum = values(3u),
                typeIsFinal = values(4u),
                default = values(5u)
            )
    }
}
