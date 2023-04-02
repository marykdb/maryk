package maryk.core.properties.definitions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.exceptions.RequestException
import maryk.core.models.ContextualDataModel
import maryk.core.models.IsObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.models.IsValuesDataModel
import maryk.core.properties.definitions.contextual.ContextTransformerDefinition
import maryk.core.properties.definitions.contextual.ContextualCollectionDefinition
import maryk.core.properties.definitions.wrapper.DefinitionWrapperDelegateLoader
import maryk.core.properties.definitions.wrapper.ObjectDefinitionWrapperDelegateLoader
import maryk.core.properties.definitions.wrapper.SetDefinitionWrapper
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.query.DefinitionsContext
import maryk.core.values.ObjectValues

/** Definition for Set property */
data class SetDefinition<T : Any, CX : IsPropertyContext> internal constructor(
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val minSize: UInt? = null,
    override val maxSize: UInt? = null,
    override val valueDefinition: IsValueDefinition<T, CX>,
    override val default: Set<T>? = null
) : IsSetDefinition<T, CX>,
    IsUsableInMapValue<Set<T>, CX>,
    IsUsableInMultiType<Set<T>, CX>,
    IsTransportablePropertyDefinitionType<Set<T>> {
    override val propertyDefinitionType = PropertyDefinitionType.Set

    init {
        require(valueDefinition.required) { "Definition for value should have required=true on set" }
    }

    constructor(
        required: Boolean = true,
        final: Boolean = false,
        minSize: UInt? = null,
        maxSize: UInt? = null,
        valueDefinition: IsUsableInCollection<T, CX>,
        default: Set<T>? = null
    ) : this(required, final, minSize, maxSize, valueDefinition as IsValueDefinition<T, CX>, default)

    object Model : ContextualDataModel<SetDefinition<*, *>, Model, ContainsDefinitionsContext, SetDefinitionContext>(
        contextTransformer = { SetDefinitionContext(it) },
    ) {
        val required by boolean(1u, SetDefinition<*, *>::required, default = true)
        val final by boolean(2u, SetDefinition<*, *>::final, default = false)
        val minSize by number(3u, SetDefinition<*, *>::minSize, type = UInt32)
        val maxSize by number(4u, SetDefinition<*, *>::maxSize, type = UInt32)
        val valueDefinition by contextual(
            index = 5u,
            getter = SetDefinition<*, *>::valueDefinition,
            definition = ContextTransformerDefinition(
                contextTransformer = { it?.definitionsContext },
                definition = InternalMultiTypeDefinition(
                    typeEnum = PropertyDefinitionType,
                    definitionMap = mapOfPropertyDefEmbeddedObjectDefinitions
                )
            ),
            toSerializable = { value, _ ->
                val defType = value as? IsTransportablePropertyDefinitionType<*>
                    ?: throw RequestException("$value is not transportable")
                TypedValue(defType.propertyDefinitionType, defType)
            },
            fromSerializable = {
                @Suppress("UNCHECKED_CAST")
                it?.value as IsValueDefinition<Any, DefinitionsContext>?
            },
            capturer = { context: SetDefinitionContext, value ->
                @Suppress("UNCHECKED_CAST")
                context.valueDefinion = value.value as IsValueDefinition<Any, ContainsDefinitionsContext>
            }
        )

        val default by contextual(
            index = 6u,
            getter = SetDefinition<*, *>::default,
            definition = ContextualCollectionDefinition(
                required = false,
                contextualResolver = { context: SetDefinitionContext? ->
                    context?.setDefinition?.let {
                        @Suppress("UNCHECKED_CAST")
                        it as IsSerializablePropertyDefinition<Collection<Any>, SetDefinitionContext>
                    } ?: throw ContextNotFoundException()
                }
            )
        )

        override fun invoke(values: ObjectValues<SetDefinition<*, *>, Model>) = SetDefinition(
            required = values(1u),
            final = values(2u),
            minSize = values(3u),
            maxSize = values(4u),
            valueDefinition = values<IsValueDefinition<*, *>>(5u),
            default = values(6u)
        )
    }
}

class SetDefinitionContext(
    val definitionsContext: ContainsDefinitionsContext?
) : IsPropertyContext {
    var valueDefinion: IsValueDefinition<Any, ContainsDefinitionsContext>? = null

    val setDefinition by lazy {
        SetDefinition(valueDefinition = this.valueDefinion ?: throw ContextNotFoundException())
    }
}

fun <T: Any, CX: IsPropertyContext> IsValuesDataModel.set(
    index: UInt,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    minSize: UInt? = null,
    maxSize: UInt? = null,
    valueDefinition: IsValueDefinition<T, CX>,
    default: Set<T>? = null,
    alternativeNames: Set<String>? = null
) = DefinitionWrapperDelegateLoader(this) { propName ->
    SetDefinitionWrapper(
        index,
        name ?: propName,
        SetDefinition(required, final, minSize, maxSize, valueDefinition, default),
        alternativeNames
    )
}

fun <T: Any, DO: Any, CX: IsPropertyContext> IsObjectDataModel<DO>.set(
    index: UInt,
    getter: (DO) -> Set<T>?,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    minSize: UInt? = null,
    maxSize: UInt? = null,
    valueDefinition: IsValueDefinition<T, CX>,
    default: Set<T>? = null,
    alternativeNames: Set<String>? = null,
    capturer: (Unit.(CX, Set<T>) -> Unit)? = null
) = ObjectDefinitionWrapperDelegateLoader(this) { propName ->
    SetDefinitionWrapper(
        index,
        name ?: propName,
        SetDefinition(required, final, minSize, maxSize, valueDefinition, default),
        alternativeNames,
        getter = getter,
        capturer = capturer
    )
}
