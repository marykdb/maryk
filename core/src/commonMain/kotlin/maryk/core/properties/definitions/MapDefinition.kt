package maryk.core.properties.definitions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.exceptions.RequestException
import maryk.core.models.ContextualDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextTransformerDefinition
import maryk.core.properties.definitions.contextual.ContextualMapDefinition
import maryk.core.properties.definitions.wrapper.MapDefinitionWrapper
import maryk.core.properties.types.TypedValue
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.values.SimpleObjectValues

/** Definition for Map property */
data class MapDefinition<K : Any, V : Any, CX : IsPropertyContext> internal constructor(
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val minSize: UInt? = null,
    override val maxSize: UInt? = null,
    override val keyDefinition: IsSimpleValueDefinition<K, CX>,
    override val valueDefinition: IsSubDefinition<V, CX>,
    override val default: Map<K, V>? = null
) :
    IsUsableInMapValue<Map<K, V>, CX>,
    IsUsableInMultiType<Map<K, V>, CX>,
    IsMapDefinition<K, V, CX>,
    HasSizeDefinition,
    IsTransportablePropertyDefinitionType<Map<K, V>>,
    HasDefaultValueDefinition<Map<K, V>>,
    IsWrappableDefinition<Map<K, V>, CX, MapDefinitionWrapper<K, V, Map<K, V>, CX, Any>> {
    override val propertyDefinitionType = PropertyDefinitionType.Map

    init {
        require(keyDefinition.required) { "Definition for key should be required on map" }
        require(valueDefinition.required) { "Definition for value should be required on map" }
    }

    constructor(
        required: Boolean = true,
        final: Boolean = false,
        minSize: UInt? = null,
        maxSize: UInt? = null,
        keyDefinition: IsSimpleValueDefinition<K, CX>,
        valueDefinition: IsUsableInMapValue<V, CX>,
        default: Map<K, V>? = null
    ) : this(required, final, minSize, maxSize, keyDefinition, valueDefinition as IsSubDefinition<V, CX>, default)

    override fun wrap(
        index: UInt,
        name: String,
        alternativeNames: Set<String>?
    ) =
        MapDefinitionWrapper<K, V, Map<K, V>, CX, Any>(index, name, this, alternativeNames)

    object Model :
        ContextualDataModel<MapDefinition<*, *, *>, ObjectPropertyDefinitions<MapDefinition<*, *, *>>, ContainsDefinitionsContext, KeyValueDefinitionContext>(
            contextTransformer = { KeyValueDefinitionContext(it) },
            properties = object : ObjectPropertyDefinitions<MapDefinition<*, *, *>>() {
                init {
                    IsPropertyDefinition.addRequired(this, MapDefinition<*, *, *>::required)
                    IsPropertyDefinition.addFinal(this, MapDefinition<*, *, *>::final)
                    HasSizeDefinition.addMinSize(3u, this, MapDefinition<*, *, *>::minSize)
                    HasSizeDefinition.addMaxSize(4u, this, MapDefinition<*, *, *>::maxSize)

                    add(5u, "keyDefinition",
                        ContextTransformerDefinition(
                            contextTransformer = { it?.definitionsContext },
                            definition = InternalMultiTypeDefinition(
                                typeEnum = PropertyDefinitionType,
                                definitionMap = mapOfPropertyDefEmbeddedObjectDefinitions
                            )
                        ),
                        getter = MapDefinition<*, *, *>::keyDefinition,
                        toSerializable = { value, _ ->
                            val defType = value as? IsTransportablePropertyDefinitionType<*>
                                ?: throw RequestException("$value is not transportable")
                            TypedValue(defType.propertyDefinitionType, defType)
                        },
                        fromSerializable = {
                            it?.value as IsSimpleValueDefinition<*, *>?
                        },
                        capturer = { context: KeyValueDefinitionContext, value: TypedValue<PropertyDefinitionType, *> ->
                            @Suppress("UNCHECKED_CAST")
                            context.keyDefinition = value.value as IsSimpleValueDefinition<Any, IsPropertyContext>
                        }
                    )

                    add(6u, "valueDefinition",
                        ContextTransformerDefinition(
                            contextTransformer = { it?.definitionsContext },
                            definition = InternalMultiTypeDefinition(
                                typeEnum = PropertyDefinitionType,
                                definitionMap = mapOfPropertyDefEmbeddedObjectDefinitions
                            )
                        ),
                        getter = MapDefinition<*, *, *>::valueDefinition,
                        toSerializable = { value, _ ->
                            val defType = value as? IsTransportablePropertyDefinitionType<*>
                                ?: throw RequestException("$value is not transportable")
                            TypedValue(defType.propertyDefinitionType, value)
                        },
                        fromSerializable = {
                            it?.value as IsSubDefinition<*, *>?
                        },
                        capturer = { context: KeyValueDefinitionContext, value ->
                            @Suppress("UNCHECKED_CAST")
                            context.valueDefinition = value.value as IsSubDefinition<Any, IsPropertyContext>
                        }
                    )

                    @Suppress("UNCHECKED_CAST")
                    add(
                        7u, "default",
                        ContextualMapDefinition(
                            contextualResolver = { context: KeyValueDefinitionContext? ->
                                context?.mapDefinition ?: throw ContextNotFoundException()
                            },
                            required = false
                        ) as IsContextualEncodable<Map<out Any, Any>, KeyValueDefinitionContext>,
                        MapDefinition<*, *, *>::default
                    )
                }
            }
        ) {
        override fun invoke(values: SimpleObjectValues<MapDefinition<*, *, *>>) = MapDefinition(
            required = values(1u),
            final = values(2u),
            minSize = values(3u),
            maxSize = values(4u),
            keyDefinition = values<IsSimpleValueDefinition<*, *>>(5u),
            valueDefinition = values<IsSubDefinition<*, *>>(6u),
            default = values(7u)
        )
    }
}

/**
 * Context to help creation of map definition
 */
class KeyValueDefinitionContext(
    val definitionsContext: ContainsDefinitionsContext?,
    var keyDefinition: IsSimpleValueDefinition<Any, IsPropertyContext>? = null,
    var valueDefinition: IsSubDefinition<Any, IsPropertyContext>? = null
) : IsPropertyContext {
    val mapDefinition by lazy {
        MapDefinition(
            keyDefinition = this.keyDefinition ?: throw ContextNotFoundException(),
            valueDefinition = this.valueDefinition ?: throw ContextNotFoundException()
        )
    }
}
