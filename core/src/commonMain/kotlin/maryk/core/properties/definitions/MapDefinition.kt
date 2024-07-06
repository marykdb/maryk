package maryk.core.properties.definitions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.exceptions.RequestException
import maryk.core.models.ContextualDataModel
import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.contextual.ContextTransformerDefinition
import maryk.core.properties.definitions.contextual.ContextualMapDefinition
import maryk.core.properties.definitions.wrapper.DefinitionWrapperDelegateLoader
import maryk.core.properties.definitions.wrapper.MapDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ObjectDefinitionWrapperDelegateLoader
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.values.ObjectValues

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
    HasDefaultValueDefinition<Map<K, V>> {
    override val propertyDefinitionType = PropertyDefinitionType.Map

    init {
        require(keyDefinition.required) { "Definition for key should be required on map" }
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

    object Model : ContextualDataModel<MapDefinition<*, *, *>, Model, ContainsDefinitionsContext, KeyValueDefinitionContext>(
        contextTransformer = { KeyValueDefinitionContext(it) },
    ) {
        val required by boolean(1u, MapDefinition<*, *, *>::required, default = true)
        val final by boolean(2u, MapDefinition<*, *, *>::final, default = false)
        val minSize by number(3u, MapDefinition<*, *, *>::minSize, type = UInt32)
        val maxSize by number(4u, MapDefinition<*, *, *>::maxSize, type = UInt32)
        val keyDefinition by contextual(
            index = 5u,
            getter = MapDefinition<*, *, *>::keyDefinition,
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
                it?.value as IsSimpleValueDefinition<*, *>?
            },
            capturer = { context: KeyValueDefinitionContext, value: TypedValue<PropertyDefinitionType, *> ->
                @Suppress("UNCHECKED_CAST")
                context.keyDefinition = value.value as IsSimpleValueDefinition<Any, IsPropertyContext>
            }
        )

        val valueDefinition by contextual(
            index = 6u,
            getter = MapDefinition<*, *, *>::valueDefinition,
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
        val default by contextual(
            index = 7u,
            getter = MapDefinition<*, *, *>::default,
            definition = ContextualMapDefinition(
                contextualResolver = { context: KeyValueDefinitionContext? ->
                    context?.mapDefinition ?: throw ContextNotFoundException()
                },
                required = false
            ) as IsContextualEncodable<Map<out Any, Any>, KeyValueDefinitionContext>
        )

        override fun invoke(values: ObjectValues<MapDefinition<*, *, *>, Model>) = MapDefinition<Any, Any, IsPropertyContext>(
            required = values(1u),
            final = values(2u),
            minSize = values(3u),
            maxSize = values(4u),
            keyDefinition = values(5u),
            valueDefinition = values(6u),
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

fun <K : Any, V : Any, CX : IsPropertyContext> IsValuesDataModel.map(
    index: UInt,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    minSize: UInt? = null,
    maxSize: UInt? = null,
    keyDefinition: IsSimpleValueDefinition<K, CX>,
    valueDefinition: IsSubDefinition<V, CX>,
    default: Map<K, V>? = null,
    alternativeNames: Set<String>? = null
) = DefinitionWrapperDelegateLoader(this) { propName ->
    MapDefinitionWrapper<K, V, Map<K, V>, CX, Any>(
        index,
        name ?: propName,
        MapDefinition(required, final, minSize, maxSize, keyDefinition, valueDefinition, default),
        alternativeNames
    )
}

fun <K : Any, V : Any, TO: Any, DO: Any, CX: IsPropertyContext> IsObjectDataModel<DO>.map(
    index: UInt,
    getter: (DO) -> TO?,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    minSize: UInt? = null,
    maxSize: UInt? = null,
    keyDefinition: IsSimpleValueDefinition<K, CX>,
    valueDefinition: IsSubDefinition<V, CX>,
    default: Map<K, V>? = null,
    alternativeNames: Set<String>? = null,
    toSerializable: (Unit.(TO?, CX?) -> Map<K, V>?)? = null,
    fromSerializable: (Unit.(Map<K, V>?) -> TO?)? = null,
    shouldSerialize: (Unit.(Any) -> Boolean)? = null,
    capturer: (Unit.(CX, Map<K, V>) -> Unit)? = null
) = ObjectDefinitionWrapperDelegateLoader(this) { propName ->
    MapDefinitionWrapper(
        index,
        name ?: propName,
        MapDefinition(required, final, minSize, maxSize, keyDefinition, valueDefinition, default),
        alternativeNames,
        getter = getter,
        capturer = capturer,
        toSerializable = toSerializable,
        fromSerializable = fromSerializable,
        shouldSerialize = shouldSerialize
    )
}
