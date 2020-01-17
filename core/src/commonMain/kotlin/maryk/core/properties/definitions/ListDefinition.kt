package maryk.core.properties.definitions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.exceptions.RequestException
import maryk.core.models.ContextualDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextTransformerDefinition
import maryk.core.properties.definitions.contextual.ContextualCollectionDefinition
import maryk.core.properties.definitions.wrapper.ListDefinitionWrapper
import maryk.core.properties.types.TypedValue
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
    IsTransportablePropertyDefinitionType<List<T>>,
    IsWrappableDefinition<List<T>, CX, ListDefinitionWrapper<T, T, CX, Any>> {
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

    override fun wrap(
        index: UInt,
        name: String,
        alternativeNames: Set<String>?
    ) =
        ListDefinitionWrapper<T, T, CX, Any>(index, name, this, alternativeNames)

    object Model :
        ContextualDataModel<ListDefinition<*, *>, ObjectPropertyDefinitions<ListDefinition<*, *>>, ContainsDefinitionsContext, ListDefinitionContext>(
            contextTransformer = { ListDefinitionContext(it) },
            properties = object : ObjectPropertyDefinitions<ListDefinition<*, *>>() {
                init {
                    IsPropertyDefinition.addRequired(this, ListDefinition<*, *>::required)
                    IsPropertyDefinition.addFinal(this, ListDefinition<*, *>::final)
                    HasSizeDefinition.addMinSize(3u, this, ListDefinition<*, *>::minSize)
                    HasSizeDefinition.addMaxSize(4u, this, ListDefinition<*, *>::maxSize)
                    add(5u, "valueDefinition",
                        ContextTransformerDefinition(
                            contextTransformer = { it?.definitionsContext },
                            definition = InternalMultiTypeDefinition(
                                typeEnum = PropertyDefinitionType,
                                definitionMap = mapOfPropertyDefEmbeddedObjectDefinitions
                            )
                        ),
                        getter = ListDefinition<*, *>::valueDefinition,
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
                    add(6u, "default", ContextualCollectionDefinition(
                        required = false,
                        contextualResolver = { context: ListDefinitionContext? ->
                            @Suppress("UNCHECKED_CAST")
                            context?.listDefinition?.let {
                                it as IsSerializablePropertyDefinition<Collection<Any>, ListDefinitionContext>
                            } ?: throw ContextNotFoundException()
                        }
                    ), ListDefinition<*, *>::default)
                }
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
