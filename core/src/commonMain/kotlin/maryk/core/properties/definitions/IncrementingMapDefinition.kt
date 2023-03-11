package maryk.core.properties.definitions

import maryk.core.exceptions.RequestException
import maryk.core.models.ContextualDataModel
import maryk.core.properties.ContextualModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextTransformerDefinition
import maryk.core.properties.definitions.wrapper.DefinitionWrapperDelegateLoader
import maryk.core.properties.definitions.wrapper.IncMapDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ObjectDefinitionWrapperDelegateLoader
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.NumberDescriptor
import maryk.core.properties.types.numeric.NumberType
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.values.ObjectValues

/** Definition for Map property in which the key auto increments */
data class IncrementingMapDefinition<K : Comparable<K>, V : Any, CX : IsPropertyContext> internal constructor(
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val minSize: UInt? = null,
    override val maxSize: UInt? = null,
    override val keyDefinition: NumberDefinition<K>,
    override val valueDefinition: IsSubDefinition<V, CX>
) :
    IsUsableInMapValue<Map<K, V>, CX>,
    IsUsableInMultiType<Map<K, V>, CX>,
    IsMapDefinition<K, V, CX>,
    IsTransportablePropertyDefinitionType<Map<K, V>> {
    override val propertyDefinitionType = PropertyDefinitionType.IncMap

    val keyNumberDescriptor get() = keyDefinition.type

    init {
        require(keyDefinition.required) { "Definition for key should be required on map" }
        require(valueDefinition.required) { "Definition for value should be required on map" }
    }

    constructor(
        required: Boolean = true,
        final: Boolean = false,
        minSize: UInt? = null,
        maxSize: UInt? = null,
        keyNumberDescriptor: NumberDescriptor<K>,
        valueDefinition: IsUsableInMapValue<V, CX>
    ) : this(
        required,
        final,
        minSize,
        maxSize,
        NumberDefinition(type = keyNumberDescriptor, reversedStorage = true),
        valueDefinition as IsSubDefinition<V, CX>
    )

    object Model : ContextualModel<IncrementingMapDefinition<*, *, *>, Model, ContainsDefinitionsContext, KeyValueDefinitionContext>(
        contextTransformer = { KeyValueDefinitionContext(it) },
    ) {
        val required by boolean(1u, IncrementingMapDefinition<*, *, *>::required, default = true)
        val final by boolean(2u, IncrementingMapDefinition<*, *, *>::final, default = false)

        val minSize by number(
            index = 3u,
            getter = IncrementingMapDefinition<*, *, *>::minSize,
            type = UInt32
        )

        val maxSize by number(
            index = 4u,
            getter = IncrementingMapDefinition<*, *, *>::maxSize,
            type = UInt32
        )

        @Suppress("UNCHECKED_CAST")
        val keyNumberDescriptor by enum(
            index = 5u,
            getter = IncrementingMapDefinition<*, *, *>::keyNumberDescriptor as (IncrementingMapDefinition<*, *, *>) -> NumberDescriptor<Comparable<Any>>?,
            enum = NumberType,
            fromSerializable = { value: NumberType? ->
                value?.let {
                    it.descriptor() as NumberDescriptor<Comparable<Any>>
                }
            },
            toSerializable = { value: NumberDescriptor<Comparable<Any>>?, _: KeyValueDefinitionContext? ->
                value?.type
            }
        )

        val valueDefinition by contextual(
            index = 6u,
            definition = ContextTransformerDefinition(
                contextTransformer = { it?.definitionsContext },
                definition = InternalMultiTypeDefinition(
                    typeEnum = PropertyDefinitionType,
                    definitionMap = mapOfPropertyDefEmbeddedObjectDefinitions
                )
            ),
            getter = IncrementingMapDefinition<*, *, *>::valueDefinition,
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

        override fun invoke(values: ObjectValues<IncrementingMapDefinition<*, *, *>, Model>): IncrementingMapDefinition<*, *, *> =
            Model.invoke(values)

        override val Model = object : ContextualDataModel<IncrementingMapDefinition<*, *, *>, Model, ContainsDefinitionsContext, KeyValueDefinitionContext>(
            contextTransformer = contextTransformer,
            properties = this,
        ) {
            override fun invoke(values: ObjectValues<IncrementingMapDefinition<*, *, *>, Model>) = IncrementingMapDefinition<Comparable<Any>, Any, IsPropertyContext>(
                required = values(1u),
                final = values(2u),
                minSize = values(3u),
                maxSize = values(4u),
                keyDefinition = NumberDefinition(
                    type = values(5u),
                    reversedStorage = true
                ),
                valueDefinition = values(6u)
            )
        }
    }
}

fun <K : Comparable<K>, V : Any, CX : IsPropertyContext> IsValuesPropertyDefinitions.incrementingMap(
    index: UInt,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    minSize: UInt? = null,
    maxSize: UInt? = null,
    keyNumberDescriptor: NumberDescriptor<K>,
    valueDefinition: IsUsableInMapValue<V, CX>,
    alternativeNames: Set<String>? = null
) = DefinitionWrapperDelegateLoader(this) { propName ->
    IncMapDefinitionWrapper<K, V, Map<K, V>, CX, Any>(
        index,
        name ?: propName,
        IncrementingMapDefinition(required, final, minSize, maxSize, keyNumberDescriptor, valueDefinition),
        alternativeNames
    )
}

@Suppress("unused")
fun <K : Comparable<K>, V : Any, TO: Any, DO: Any, CX: IsPropertyContext> ObjectPropertyDefinitions<DO>.incrementingMap(
    index: UInt,
    getter: (DO) -> TO?,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    minSize: UInt? = null,
    maxSize: UInt? = null,
    keyNumberDescriptor: NumberDescriptor<K>,
    valueDefinition: IsUsableInMapValue<V, CX>,
    alternativeNames: Set<String>? = null,
    toSerializable: (Unit.(TO?, CX?) -> Map<K, V>?)? = null,
    fromSerializable: (Unit.(Map<K, V>?) -> TO?)? = null,
    shouldSerialize: (Unit.(Any) -> Boolean)? = null,
    capturer: (Unit.(CX, Map<K, V>) -> Unit)? = null
) = ObjectDefinitionWrapperDelegateLoader(this) { propName ->
    IncMapDefinitionWrapper(
        index,
        name ?: propName,
        definition = IncrementingMapDefinition(required, final, minSize, maxSize, keyNumberDescriptor, valueDefinition),
        alternativeNames = alternativeNames,
        getter = getter,
        capturer = capturer,
        toSerializable = toSerializable,
        fromSerializable = fromSerializable,
        shouldSerialize = shouldSerialize
    )
}
