package maryk.core.properties.definitions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.exceptions.RequestException
import maryk.core.models.ContextualDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextTransformerDefinition
import maryk.core.properties.definitions.contextual.ContextualCollectionDefinition
import maryk.core.properties.definitions.wrapper.DefinitionWrapperDelegateLoader
import maryk.core.properties.definitions.wrapper.ListDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ObjectDefinitionWrapperDelegateLoader
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.values.SimpleObjectValues

/** Definition for List property */
data class ListDefinition<T : Any, CX : IsPropertyContext> internal constructor(
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val minSize: UInt? = null,
    override val maxSize: UInt? = null,
    override val valueDefinition: IsValueDefinition<T, CX>,
    override val default: List<T>? = null
) : IsListDefinition<T, CX>,
    IsUsableInMapValue<List<T>, CX>,
    IsUsableInMultiType<List<T>, CX>,
    IsTransportablePropertyDefinitionType<List<T>> {
    override val propertyDefinitionType = PropertyDefinitionType.List

    init {
        require(valueDefinition.required) { "Definition for value should have required=true on List" }
    }

    constructor(
        required: Boolean = true,
        final: Boolean = false,
        minSize: UInt? = null,
        maxSize: UInt? = null,
        valueDefinition: IsUsableInCollection<T, CX>,
        default: List<T>? = null
    ) : this(required, final, minSize, maxSize, valueDefinition as IsValueDefinition<T, CX>, default)

    @Suppress("unused")
    object Model :
        ContextualDataModel<ListDefinition<*, *>, ObjectPropertyDefinitions<ListDefinition<*, *>>, ContainsDefinitionsContext, ListDefinitionContext>(
            contextTransformer = { ListDefinitionContext(it) },
            properties = object : ObjectPropertyDefinitions<ListDefinition<*, *>>() {
                val required by boolean(1u, ListDefinition<*, *>::required, default = true)
                val final by boolean(2u, ListDefinition<*, *>::final, default = false)
                val minSize by number(3u, ListDefinition<*, *>::minSize, type = UInt32)
                val maxSize by number(4u, ListDefinition<*, *>::maxSize, type = UInt32)
                val valueDefinition by contextual(
                    index = 5u,
                    getter = ListDefinition<*, *>::valueDefinition,
                    definition = ContextTransformerDefinition(
                        contextTransformer = { it?.definitionsContext },
                        definition = InternalMultiTypeDefinition(
                            typeEnum = PropertyDefinitionType,
                            definitionMap = mapOfPropertyDefEmbeddedObjectDefinitions
                        )
                    ),
                    toSerializable = { value, _ ->
                        val defType = value as? IsTransportablePropertyDefinitionType<*> ?: throw RequestException("$value is not transportable")
                        TypedValue(defType.propertyDefinitionType, defType)
                    },
                    fromSerializable = {
                        it?.value as IsValueDefinition<*, *>?
                    },
                    capturer = { context: ListDefinitionContext?, value ->
                        context?.apply {
                            @Suppress("UNCHECKED_CAST")
                            valueDefinition = value.value as IsValueDefinition<Any, IsPropertyContext>
                        } ?: throw ContextNotFoundException()
                    }
                )
                val default by contextual(
                    index = 6u,
                    getter = ListDefinition<*, *>::default,
                    definition = ContextualCollectionDefinition(
                        required = false,
                        contextualResolver = { context: ListDefinitionContext? ->
                            @Suppress("UNCHECKED_CAST")
                            context?.listDefinition?.let {
                                it as IsSerializablePropertyDefinition<Collection<Any>, ListDefinitionContext>
                            } ?: throw ContextNotFoundException()
                        }
                    )
                )
            }
        ) {
        override fun invoke(values: SimpleObjectValues<ListDefinition<*, *>>) = ListDefinition(
            required = values(1u),
            final = values(2u),
            minSize = values(3u),
            maxSize = values(4u),
            valueDefinition = values<IsValueDefinition<*, *>>(5u),
            default = values(6u)
        )
    }
}

class ListDefinitionContext(
    val definitionsContext: ContainsDefinitionsContext?,
    var valueDefinition: IsValueDefinition<Any, IsPropertyContext>? = null
) : IsPropertyContext {
    val listDefinition by lazy {
        ListDefinition(valueDefinition = this.valueDefinition ?: throw ContextNotFoundException())
    }
}

fun <T: Any, CX: IsPropertyContext> IsValuesPropertyDefinitions.list(
    index: UInt,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    minSize: UInt? = null,
    maxSize: UInt? = null,
    valueDefinition: IsValueDefinition<T, CX>,
    default: List<T>? = null,
    alternativeNames: Set<String>? = null
) = DefinitionWrapperDelegateLoader(this) { propName ->
    ListDefinitionWrapper<T, T, CX, Any>(
        index,
        name ?: propName,
        ListDefinition(required, final, minSize, maxSize, valueDefinition, default),
        alternativeNames
    )
}

fun <T: Any, TO: Any, DO: Any, CX: IsPropertyContext> ObjectPropertyDefinitions<DO>.list(
    index: UInt,
    getter: (DO) -> List<TO>?,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    minSize: UInt? = null,
    maxSize: UInt? = null,
    valueDefinition: IsValueDefinition<T, CX>,
    default: List<T>? = null,
    alternativeNames: Set<String>? = null,
    toSerializable: (Unit.(TO) -> T)? = null,
    fromSerializable: (Unit.(T) -> TO)? = null,
    shouldSerialize: (Unit.(Any) -> Boolean)? = null,
    capturer: (Unit.(CX, List<T>) -> Unit)? = null
) = ObjectDefinitionWrapperDelegateLoader(this) { propName ->
    @Suppress("UNCHECKED_CAST")
    ListDefinitionWrapper(
        index,
        name ?: propName,
        ListDefinition(required, final, minSize, maxSize, valueDefinition, default),
        alternativeNames,
        getter = getter,
        capturer = capturer,
        toSerializable = toSerializable?.let { toSerializable ->
            val toSerializableList: Unit.(List<TO>?, CX?) -> List<T>? = { value: List<TO>?, _: CX? ->
                value?.map { toSerializable(Unit, it) }
            }
            toSerializableList
        },
        fromSerializable = fromSerializable?.let { fromSerializable ->
            val fromSerializableList: Unit.(List<T>?) -> List<TO>? = { value: List<T>? ->
                value?.map { fromSerializable(Unit, it) }
            }
            fromSerializableList
        },
        shouldSerialize = shouldSerialize
    )
}
