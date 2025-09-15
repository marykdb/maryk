package maryk.core.properties.definitions.wrapper

import maryk.core.exceptions.DefNotFoundException
import maryk.core.inject.Inject
import maryk.core.models.IsValuesDataModel
import maryk.core.models.SimpleObjectModel
import maryk.core.models.ValuesCollectorContext
import maryk.core.models.serializers.ObjectDataModelSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSerializablePropertyDefinition
import maryk.core.properties.definitions.IsTransportablePropertyDefinitionType
import maryk.core.properties.definitions.PropertyDefinitionType
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.internalMultiType
import maryk.core.properties.definitions.mapOfPropertyDefEmbeddedObjectDefinitions
import maryk.core.properties.definitions.mapOfPropertyDefWrappers
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.set
import maryk.core.properties.definitions.string
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.graph.IsPropRefGraphNode
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt32
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.values.MutableValueItems
import maryk.core.values.ObjectValues
import maryk.core.values.ValueItem
import maryk.core.yaml.readNamedIndexField
import maryk.core.yaml.writeNamedIndexField
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.yaml.IsYamlReader
import maryk.yaml.YamlWriter

typealias AnyDefinitionWrapper = IsDefinitionWrapper<Any, Any, IsPropertyContext, Any>
typealias AnyTypedDefinitionWrapper<DO> = IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>
typealias AnyOutDefinitionWrapper = IsDefinitionWrapper<out Any, out Any, IsPropertyContext, Any>

/**
 * Wraps a Property Definition of type [T] to give it more context [CX] about
 * DataObject [DO] which contains this Definition.
 */
interface IsDefinitionWrapper<T : Any, TO : Any, in CX : IsPropertyContext, in DO> :
    IsSerializablePropertyDefinition<T, CX>,
    IsPropRefGraphNode<IsValuesDataModel> {
    override val index: UInt
    val name: String
    val alternativeNames: Set<String>?
    val definition: IsSerializablePropertyDefinition<T, CX>
    val getter: (DO) -> TO?
    val capturer: ((CX, T) -> Unit)?
    val toSerializable: ((TO?, CX?) -> T?)?
    val fromSerializable: ((T?) -> TO?)?
    val shouldSerialize: ((Any) -> Boolean)?

    /** Create an index [value] pair for maps */
    infix fun asValueItem(value: Any): ValueItem {
        return ValueItem(this.index, value)
    }

    /** DSL support: add value via += inside a create { } block */
    operator fun minusAssign(value: T?) { ValuesCollectorContext.add(value?.let {
        ValueItem(this.index, value)
    }) }

    /** DSL support: add value via += inside a create { } block */
    infix fun with(value: TO?) {
        value?.let {
            val serializedValue = try {
                if (shouldSerialize == null || shouldSerialize!!(value)) {
                    toSerializable?.let { serializer ->
                        serializer(value, null) ?: value
                    } ?: value
                } else {
                    value
                }
            } catch (_: Throwable) {
                value
            }

            ValueItem(this.index, serializedValue).also { ValuesCollectorContext.add(it) }
        }
    }

    /** DSL support: inject via += inside a create { } block */
    infix fun with(inject: Inject<*, *>) {
        inject.let { ValuesCollectorContext.add(ValueItem(this.index, it)) }
    }

    /** Get a reference to this definition inside [parentRef] */
    fun ref(parentRef: AnyPropertyReference? = null): IsPropertyReference<T, IsPropertyDefinition<T>, *>

    /**
     * Validates [newValue] against [previousValue] on propertyDefinition and if fails creates
     * reference with [parentRefFactory]
     * @throws ValidationException when encountering invalid new value
     */
    fun validate(previousValue: T? = null, newValue: T?, parentRefFactory: () -> AnyPropertyReference? = { null }) {
        this.validateWithRef(previousValue, newValue) { this.ref(parentRefFactory()) }
    }

    /** Calculates the needed byte size to transport [value] within optional [context] and caches it with [cacher] */
    fun calculateTransportByteLengthWithKey(value: T, cacher: WriteCacheWriter, context: CX? = null) =
        this.calculateTransportByteLengthWithKey(this.index.toInt(), value, cacher, context)

    /**
     * Writes [value] to bytes with [writer] for transportation and adds the key with tag and wire type.
     * Uses the [cacheGetter] to get length.
     * Optionally pass a [context] to write more complex properties which depend on other properties
     */
    fun writeTransportBytesWithKey(
        value: T,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: CX? = null
    ) =
        this.writeTransportBytesWithKey(this.index.toInt(), value, cacheGetter, writer, context)

    /** Get the property from the [dataObject] and serialize it for transportation */
    fun getPropertyAndSerialize(dataObject: DO, context: CX?): T? {
        @Suppress("UNCHECKED_CAST")
        return this.toSerializable?.let {
            return it.invoke(this.getter(dataObject), context)
        } ?: this.getter(dataObject) as T?
    }

    /** Capture the [value] in the [context] if needed */
    fun capture(context: CX?, value: T) {
        if (this.capturer != null && context != null) {
            this.capturer!!.invoke(context, value)
        }
    }

    /**
     * Checks if this property definition wrapper is compatible with passed definition wrapper.
     * It checks the index, names and alternative names and the definition itself.
     * It is compatible if any property validated by the passed definition
     * is accepted by this definition.
     *
     * Validation rules which are less strict are accepted but more strict or incompatible rules are not.
     */
    fun compatibleWith(
        wrapper: IsDefinitionWrapper<*, *, *, *>,
        checkedDataModelNames: MutableList<String>? = null,
        addIncompatibilityReason: ((String) -> Unit)? = null
    ): Boolean {
        var compatible = true

        if (index != wrapper.index) {
            addIncompatibilityReason?.invoke("$index: Property index ${wrapper.index} of passed property was not the same as on this definition")
            compatible = false
        }

        if (name != wrapper.name && alternativeNames?.contains(wrapper.name) != true) {
            addIncompatibilityReason?.invoke("$index: Property name ${wrapper.name} not found on this definition ($name) or in alternative names")
            compatible = false
        }

        wrapper.alternativeNames?.forEach { altName ->
            if (alternativeNames?.contains(altName) != true && name != altName) {
                addIncompatibilityReason?.invoke("$index: Alternative property name ${wrapper.name} not found on this definition name ($name) or in alternative names")
                compatible = false
            }
        }

        if (!this.definition.compatibleWith(wrapper.definition, checkedDataModelNames) { addIncompatibilityReason?.invoke("$index: $it") }) {
            compatible = false
        }

        return compatible
    }

    object Model : SimpleObjectModel<AnyOutDefinitionWrapper, Model>() {
        val index by number(1u, IsDefinitionWrapper<*, *, *, *>::index, UInt32)
        val name by string(2u, IsDefinitionWrapper<*, *, *, *>::name)
        val alternativeNames by set(
            index = 3u,
            getter = IsDefinitionWrapper<*, *, *, *>::alternativeNames,
            valueDefinition = StringDefinition()
        )
        val definition by internalMultiType(
            index = 4u,
            getter = {
                val def = it.definition as IsTransportablePropertyDefinitionType<*>
                TypedValue(def.propertyDefinitionType, def)
            },
            typeEnum = PropertyDefinitionType,
            definitionMap = mapOfPropertyDefEmbeddedObjectDefinitions
        )

        override fun invoke(values: ObjectValues<IsDefinitionWrapper<out Any, out Any, IsPropertyContext, Any>, Model>): IsDefinitionWrapper<out Any, out Any, IsPropertyContext, Any> {
            val typedDefinition =
                values<TypedValue<PropertyDefinitionType, IsTransportablePropertyDefinitionType<*>>>(
                    definition.index
                )
            val type = typedDefinition.type

            return mapOfPropertyDefWrappers[type]?.invoke(
                values(index.index),
                values(name.index),
                values(alternativeNames.index),
                typedDefinition.value
            ) ?: throw DefNotFoundException("Property type $type not found")
        }

        override val Serializer = object: ObjectDataModelSerializer<IsDefinitionWrapper<out Any, out Any, IsPropertyContext, Any>, Model, IsPropertyContext, IsPropertyContext>(this) {
            override fun writeObjectAsJson(
                obj: IsDefinitionWrapper<out Any, out Any, IsPropertyContext, Any>,
                writer: IsJsonLikeWriter,
                context: IsPropertyContext?,
                skip: List<IsDefinitionWrapper<*, *, *, IsDefinitionWrapper<out Any, out Any, IsPropertyContext, Any>>>?
            ) {
                // When writing YAML, use YAML optimized format with complex field names
                if (writer is YamlWriter) {
                    val typedDefinition =
                        definition.getPropertyAndSerialize(obj, context as ContainsDefinitionsContext)
                            ?: throw DefNotFoundException("Unknown type ${obj.definition} so cannot serialize contents")

                    writer.writeNamedIndexField(obj.name, obj.index, obj.alternativeNames)

                    definition.writeJsonValue(typedDefinition, writer, context)
                } else {
                    super.writeObjectAsJson(obj, writer, context, skip)
                }
            }

            override fun readJson(
                reader: IsJsonLikeReader,
                context: IsPropertyContext?
            ): ObjectValues<AnyOutDefinitionWrapper, Model> {
                // When reading YAML, use YAML optimized format with complex field names
                return if (reader is IsYamlReader) {
                    val valueMap = MutableValueItems()

                    reader.readNamedIndexField(valueMap, name, index, alternativeNames)
                    valueMap[definition.index] =
                        definition.readJson(reader, context as ContainsDefinitionsContext)

                    ObjectValues(this@Model, valueMap, context as? RequestContext)
                } else {
                    super.readJson(reader, context)
                }
            }
        }
    }
}
